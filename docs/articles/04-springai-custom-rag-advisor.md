# Spring AI 1.1.4 没有 RetrievalAugmentationAdvisor：自建 BaseAdvisor 实现 Modular RAG

> [Spring AI 中文踩坑系列] 第四篇。前几篇把 RAG 检索质量（reranker、中文分词）讲透了，这篇讲**架构**：怎么把这套检索编织进 Spring AI 的 Advisor 链，做成声明式 RAG——而不是在 Service 里手动拼 prompt。但第一步就撞墙：官方文档和大量教程提到的 `RetrievalAugmentationAdvisor` / `QuestionAnswerAdvisor`，**在 1.1.4 里根本不存在**。这是又一个 API 幻觉坑。

---

## 一、目标：声明式 RAG（Advisor 化）

初版 RAG 是在 `ChatService` 里手动编排：检索 → 拼 prompt → 调 ChatClient。这能跑，但有几个问题：

- **检索逻辑耦合在 Service 里**，复用难；
- **prompt 拼接散落**，加个"引用来源"要改 Service；
- **和 memory advisor 的执行顺序难控**——谁先谁后直接影响 memory 存的是什么。

理想形态是**声明式 RAG**：把检索+注入做成一个 Advisor，挂到 ChatClient 链上，Spring AI 自动在调用前注入上下文。Spring AI 的 Advisor 机制就是为此设计的。

但当我照着官方文档/教程写：

```java
// ❌ 编译不过：找不到这个类
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
```

IDE 一片红。去 jar 包里翻，确实没有。

---

## 二、坑：API 幻觉 —— 这些类在 1.1.4 不存在

**现象**：`RetrievalAugmentationAdvisor`、`QuestionAnswerAdvisor` 这两个被广泛引用的 RAG Advisor 类，在 Spring AI 1.1.4 里**都不存在**。

**根因**（代码注释里我记下了考证过程）：

```java
// Spring AI 1.1.4 ships no RetrievalAugmentationAdvisor / QuestionAnswerAdvisor
// (the `rag` and `advisors-vector-store` modules were dropped after the 1.0 split)
```

这两个类原本在 `rag` 和 `advisors-vector-store` 模块里，**在 1.0 的模块拆分之后被移除了**。但大量博客、教程、甚至某些版本的官方快照文档还在引用它们——你照抄，编译不过。

**更隐蔽的坑**：AI 助手（包括我自己之前的调研）也会"幻觉"出这些 API 的用法，给你一段看起来很合理的、调用 `RetrievalAugmentationAdvisor.builder()...` 的代码，但这段代码**无法编译**。这是个典型的"知识过期 + 来源污染"问题。我项目里最初的升级调研报告就栽在这里，后来靠实测验 jar 才纠正。

> 💡 **排坑方法**：碰到 Spring AI 某个 API 不确定存不存在，别信文档/博客/AI，**直接解压 BOM 对应版本的 jar 看 class**。1.1.4 的 jar 里没有就是没有。

---

## 三、正解：自建 `BaseAdvisor`

Spring AI 1.1.4 里**确实存在**的、面向这种场景的抽象是 `BaseAdvisor`。它是个接口，要求实现：

- `before(ChatClientRequest, AdvisorChain)`：调用前钩子（做检索+注入就在这里）；
- `after(ChatClientResponse, AdvisorChain)`：调用后钩子；
- `getName()`：advisor 名字；
- `getOrder()`：执行顺序。

**关键**：`BaseAdvisor` 的默认 `adviseCall` / `adviseStream` 已经帮你把 `before → 链 → after` 串好了，所以你**只实现 `before` 就够了**，call（阻塞）和 stream（流式）两条路自动都覆盖。

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class RagAdvisor implements BaseAdvisor {

    private static final String USER_TEMPLATE =
            "Context:\n%s\n\nQuestion: %s\n\nPlease cite sources in your answer.";

    private final RetrievalService retrievalService;

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // 1. 取出用户问题
        String question = request.prompt().getUserMessage().getText();
        // 2. 跑混合检索（向量 + BM25 + RRF + Rerank，全在 retrievalService 里）
        List<Document> docs = retrievalService.retrieve(question);
        // 3. 格式化上下文（带 docId + 分数，供模型引用来源）
        String contextText = formatContext(docs);
        // 4. 拼成增强后的 user message
        String augmented = String.format(USER_TEMPLATE, contextText, question);
        // 5. 用 augmentUserMessage 替换原 prompt 的 user message
        Prompt augmentedPrompt = request.prompt().augmentUserMessage(um -> new UserMessage(augmented));
        return request.mutate().prompt(augmentedPrompt).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;   // 调用后无需处理，透传
    }

    @Override
    public String getName() { return "rag-advisor"; }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

    private String formatContext(List<Document> docs) {
        return docs.stream()
                .map(doc -> {
                    String docId = doc.getMetadata().get("docId") instanceof String s ? s : "unknown";
                    double score = doc.getMetadata().get("rrfScore") instanceof Number n ? n.doubleValue() : 0.0;
                    return "[Source: " + docId + " (score: " + String.format("%.4f", score) + ")]\n" + doc.getText();
                })
                .collect(Collectors.joining("\n\n"));
    }
}
```

整个 RAG 检索（向量+BM25+RRF+Rerank，详见第三篇）现在被封装进 `RetrievalService`，Advisor 只管"检索→注入"这一步，干净分层。

---

## 四、两个关键设计决策

这段代码看起来不长，但有两个**极易做错**的设计决策，每一个都踩过坑。

### 决策 1：逐调用注册，不做默认 Advisor

注意 RagAdvisor **不是**在 ChatClient bean 上 `.defaultAdvisors(ragAdvisor)` 挂死的，而是在 `ChatService` 调用时**逐次注册**：

```java
// ChatService —— 瘦身为薄编排
public String chat(String question, String conversationId) {
    return chatClient.prompt()
            .user(question)
            .advisors(a -> a
                .param("chat_memory_conversation_id", conversationId)
                .advisors(ragAdvisor))   // ← 逐调用注册
            .call()
            .content();
}
```

**为什么不能挂默认 Advisor？** 因为这个 `chatClient` bean **是被共享的**——它同时被 Agent 工作流（`/chain`、`/route`、`/parallel` 这些 step）使用。如果把 RAG 注入挂成默认 advisor，那么 **Agent 的每一次内部调用都会被注入知识库上下文**，这会污染路由决策（RouterSelectorStep）和 ReAct 推理——模型会因为多出来的"Context"而改变行为。

逐调用注册保证了：**只有 `/chat` 这条明确的 RAG 问答路径才注入上下文，Agent 工作流路径干干净净。**

> 💡 这是个常被忽略的架构原则：**Advisor 是有副作用的（它改了 prompt），共享 ChatClient 上挂默认 Advisor 要极其谨慎**。不确定就用逐调用注册，把副作用限制在需要它的调用点。

### 决策 2：Ordering —— 必须先于 Memory Advisor

```java
@Override
public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
```

注释解释了为什么：

```java
// Ordering: HIGHEST_PRECEDENCE runs before MessageChatMemoryAdvisor
// (DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER = -2147482648), so memory captures the
// context-augmented user message — identical to the prior manual-RAG behavior.
```

Spring AI 的 `MessageChatMemoryAdvisor`（负责把对话存进 memory）有一个默认 order `DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER = -2147482648`。**RagAdvisor 必须比它更早执行（order 更小），用 `HIGHEST_PRECEDENCE`（Integer.MIN_VALUE）。**

为什么顺序重要？因为 memory advisor 存的是"它看到的那条 user message"。

- 如果 **RAG 在 memory 之前**跑：memory 存的是**增强后**的 message（`Context:... Question:...`）→ 和改造前手动 RAG 的行为**逐字节一致**，对话历史里带着上下文，模型回看历史时能看到。
- 如果 **RAG 在 memory 之后**跑：memory 存的是**原始** message（只有 question），增强发生在 memory 之后 → 行为变了，历史里没有上下文。

改造时要保证**零行为变更**（这是重构的铁律），所以 order 必须对齐，让 memory 捕获增强后的消息。

> 💡 Spring AI 里 Advisor 的 order 是个隐形坑：`HIGHEST_PRECEDENCE` 是 `Integer.MIN_VALUE`，比 memory 的 `-2147482648` 还小，所以排最前。记不住具体数字没关系，记住"RAG 注入要在 memory 之前"这个**语义顺序**即可。

---

## 五、Modular RAG：这套设计的架构意义

把 RAG 检索做成 Advisor 之后，架构变成了 **Modular RAG**：

```
ChatClient 调用
   ├── [Advisor 链]
   │     ├── RagAdvisor (HIGHEST_PRECEDENCE)：检索 + 注入上下文
   │     └── MessageChatMemoryAdvisor：存对话历史
   └── ChatModel：生成回答
```

好处：

1. **关注点分离**：检索逻辑在 `RetrievalService`，注入逻辑在 `RagAdvisor`，编排逻辑在 `ChatService`，各自可测、可换。
2. **声明式**：`ChatService` 不再手动拼 prompt，只声明"我要用 ragAdvisor"，prompt 拼接由 Advisor 负责。
3. **可组合**：以后要加 query 改写、HyDE、多路召回融合，都可以做成新的 Advisor 插进链里，不动核心。

对照 Modular RAG 论文的分层（Retriever → Reranker → Generator 之间的编排可插拔），这套 Advisor 化正好落在"编排层"。ka-rag 从最初的朴素 RAG（只向量检索）进化到这个形态，RAG eval 也随之从 6.10 一路到 8.00 满分（详见下篇）。

---

## 六、避坑速查表

| 坑 | 现象 | 根因 | 修复 |
|---|---|---|---|
| `RetrievalAugmentationAdvisor` 找不到 | 编译不过 | 1.0 模块拆分后被移除，1.1.4 无此类 | 自建 `BaseAdvisor` |
| 教程/AI 给的 RAG Advisor 代码编译不过 | API 幻觉 | 知识过期/来源污染 | 解压 jar 验 class |
| 只实现 `before` 够不够 | 担心 stream 路径 | `BaseAdvisor` 默认串好 call+stream | 够，只写 `before` |
| 共享 ChatClient 挂默认 RAG advisor | Agent 路由/推理被污染 | advisor 改 prompt 有副作用 | 逐调用注册 |
| RAG 在 memory 之后 | 历史里没上下文，行为变了 | order 没对齐 | `HIGHEST_PRECEDENCE` 先于 memory |
| `augmentUserMessage` 怎么用 | 不会替换 prompt | API 不直观 | `prompt.augmentUserMessage(um -> new UserMessage(text))` |

---

## 写在最后

这篇的核心其实不是"怎么写一个 Advisor"（那部分代码很短），而是两个**架构判断**：

1. **逐调用注册 > 默认 advisor**：当 ChatClient 被多条路径共享，advisorable 的副作用必须收窄到需要的调用点。
2. **RAG 注入要先于 memory**：保证 memory 捕获的是增强后的消息，行为零回归。

再加一个**元教训**：Spring AI 还在快速演进，API 变动频繁，文档/博客/AI 给的 API 都可能过期或幻觉。**验 jar 包是唯一可靠的真相来源**。

一句话总结：**1.1.4 没有 `RetrievalAugmentationAdvisor`，自建 `BaseAdvisor` 只实现 `before`；逐调用注册防污染；order 先于 memory 保零回归。**

下一篇是本系列的**方法论篇**：怎么搭一个 LLM-as-judge 的 eval 基准，把 RAG 质量从 6.10 一路量化优化到 8.00 满分。

> 本文代码来自真实项目 `knowledge-assistant`，RAG 已 Advisor 化并跑通端到端。
