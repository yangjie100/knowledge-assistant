# Spring AI 工具调用从"完全失效"到"稳定触发"：手搭 Advisor 为什么不执行工具？

> 这是 [Spring AI 中文踩坑系列] 第二篇。上一篇讲 GLM-5.2 集成的 6 个坑，这一篇讲一个**比那 6 个加起来还费时间**的坑：工具调用（function calling）配好了，模型也"认"得工具，但**工具从未被真正执行**——GLM 把 `tool_calls` 当成普通文本吐回来，整个 Agent 哑火。本文给出根因和一行修复，附真实可运行的 `@Tool` 代码。

---

## 一、症状：模型"会"调工具，但工具"不"执行

接入 GLM-5.2 做 ReAct Agent，问 `现在是几点？`，期望它调用 `getCurrentDateTime` 工具返回真实时间。实际输出却是：

```text
<think>用户问时间，我应该调用 getCurrentDateTime 工具</think>
<tool_call>getCurrentDateTime()</tool_call>
现在是 2024 年 1 月 1 日。   ← 瞎编的，工具根本没跑
```

注意：模型**正确识别**了该调工具，甚至**正确生成了** `<tool_call>` 调用指令，但这些指令**停留在文本里**，Spring AI 没有去执行它，模型最后只能基于自己的参数知识瞎编一个答案。

这是最坑的失效模式：**不是"工具没找到"，是"执行循环没启动"**。前者会报错，后者静默失败，你以为 Agent 工作正常，其实它一直在自欺欺人。

---

## 二、错误复现：一段"看起来很对"的手搭代码

我最初是按"网上教程"手搭的工具调用链，代码长这样（**反面教材**）：

```java
// ❌ 反面教材：手搭 ToolCallAdvisor + DefaultToolCallingManager
@Configuration
public class WrongAgentConfig {

    @Bean
    public ChatClient reactChatClient(ChatModel chatModel,
                                      List<ToolCallback> toolCallbacks) {
        // 手动构造 ToolCallbackResolver
        var resolver = new StaticToolCallbackResolver(toolCallbacks);
        // 手动构造 ToolCallingManager
        var manager = DefaultToolCallingManager.builder()
                .toolCallbackResolver(resolver)
                .build();
        // 手动挂一个 ToolCallAdvisor
        var toolAdvisor = ToolCallAdvisor.builder()
                .toolCallingManager(manager)
                .build();

        return ChatClient.builder(chatModel)
                .defaultAdvisors(toolAdvisor)   // ← 以为这样就能调工具了
                .defaultSystem("你是一个智能助手……")
                .build();
    }
}
```

这段代码**能编译、能启动、模型能"看到"工具描述**（因为 toolCallbacks 被序列化进了 system/工具 schema），但它**不会执行工具**。

---

## 三、根因：`ToolCallAdvisor` ≠ 工具执行循环

这是本文的核心。我把代码注释里的血泪直接贴出来：

```java
// P0-3b: reactChatClient uses Spring AI 1.1.x idiomatic tool mounting — defaultTools(@Tool beans)
// lets the framework auto-register ToolCallbacks AND enable internal tool execution (the
// Thought→Action→Observation loop runs inside ChatModel.call). Replaces the prior hand-built
// ToolCallAdvisor + DefaultToolCallingManager + StaticToolCallbackResolver, which failed to
// execute tools (GLM's tool_calls came back as <tool_call> text because the execution loop
// was never engaged).
```

翻译成人话：Spring AI 1.1.x 的工具调用有**两个独立的东西**，很多人混为一谈：

| 东西 | 作用 | 由谁负责 |
|---|---|---|
| **ToolCallback 注册** | 把工具描述（schema）告诉模型，让模型"知道"有哪些工具可用 | `ToolCallAdvisor` 也能做，`defaultTools` 也能做 |
| **工具执行循环（tool execution loop）** | 模型返回 `tool_calls` 后，**真正去调用方法、拿到结果、再把结果喂回模型**，循环到模型不再调工具为止 | **只有 `defaultTools(@Tool)` 这条路才会启用框架内置的执行循环** |

**手搭 `ToolCallAdvisor` 只完成了"注册"，没有启用"执行循环"。** 所以模型返回的 `tool_calls` 没人接，被当成普通文本塞进响应里。

这就像：你给员工发了工具箱的清单（注册），但**没有给他执行任务的权限和流程**（执行循环），于是他每次都把"我要用扳手"写在工作日志里，却从不动手。

---

## 四、正解：`defaultTools(@Tool beans)` 一行搞定

Spring AI 1.1.x 的**惯用法**是把工具挂在 `ChatClient.builder()` 上：

```java
private ChatClient buildReactClient(ChatModel chatModel,
                                     KnowledgeSearchTool searchTool,
                                     DateTimeTool dateTimeTool,
                                     KnowledgeStatsTool statsTool,
                                     CalculatorTool calcTool) {
    // defaultTools(@Tool beans)：框架自动注册 ToolCallback 并启用内部工具执行
    // —— 无需手动 ToolCallAdvisor / ToolCallingManager
    return ChatClient.builder(chatModel)
            .defaultSystem(SYSTEM_PROMPT)
            .defaultTools(searchTool, dateTimeTool, statsTool, calcTool)   // ✅ 关键
            .build();
}
```

`defaultTools(Object...)` 接收的是**带 `@Tool` 注解的 Spring bean**，框架会：

1. 自动扫描这些 bean 上的 `@Tool` 方法，生成 ToolCallback；
2. **启用框架内部的工具执行循环**（这才是关键）；
3. 模型返回 `tool_calls` 时，框架自动调用对应方法 → 拿结果 → 喂回模型 → 循环，直到模型给出最终答案。

**一行 `.defaultTools(...)` 替代了上面那一整段手搭代码，而且真正能执行工具。**

---

## 五、`@Tool` 工具怎么写（含安全设计）

工具本身用 `@Tool` + `@ToolParam` 注解，普通 Spring bean 即可。

### 简单工具：获取当前时间

```java
@Component
public class DateTimeTool {
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (EEEE)");

    @Tool(description = "Get the current date and time, including the day of week")
    public String getCurrentDateTime() {
        return LocalDateTime.now(ZoneId.systemDefault()).format(FMT);
    }
}
```

### 危险工具：计算器（必须做输入校验）

计算器工具如果直接 `eval` 用户输入的表达式，等于开了个**远程代码执行 (RCE)** 后门。所以必须做白名单校验：

```java
@Component
public class CalculatorTool {
    // 只允许数字和四则运算符，挡掉一切注入
    private static final Pattern SAFE = Pattern.compile("^[0-9+\\-*/().\\s]+$");

    @Tool(description = "Evaluate a mathematical expression. Supports +, -, *, /, and parentheses.")
    public String calculate(
            @ToolParam(description = "Math expression like 2+3*4") String expression) {
        if (expression == null || !SAFE.matcher(expression.trim()).matches()) {
            throw new IllegalArgumentException(
                "Invalid expression: only digits, +, -, *, /, (, ), and decimal point allowed");
        }
        // 通过校验后，用手写的递归下降 Parser 求值（不要用 ScriptEngine eval！）
        double result = new Parser(expression.trim().replaceAll("\\s+", "")).parse();
        if (Double.isInfinite(result)) return "Error: division by zero";
        if (result == (long) result) return String.valueOf((long) result);
        return String.valueOf(result);
    }
    // ... 内部 Parser 类：parse()/term()/factor() 递归下降
}
```

> ⚠️ **工具安全红线**：任何接收自由文本的工具，都**必须先做白名单校验再用**。计算器用正则白名单 + 手写 Parser，绝不用 `ScriptEngine`/`eval` 类的东西。这是 Agent 工具调用最容易被忽略的安全漏洞——模型是会被 prompt 注入骗去调危险工具的。

---

## 六、条件化双模型 + `@Qualifier` 注入坑

我的 Agent 要支持 GLM-5.2（主力）和 Ollama（兜底，无 API key 时）。这里有个**双 bean 同名 + 互斥条件**的标准写法：

```java
@Configuration
public class AgentExecutorConfig {

    @Bean("reactChatClient")
    @ConditionalOnExpression("'${spring.ai.zhipuai.api-key:}' != ''")
    public ChatClient reactChatClientGlm(ZhiPuAiChatModel chatModel, /* tools */) {
        return buildReactClient(chatModel, /* tools */);   // GLM 原生 function calling
    }

    @Bean("reactChatClient")
    @ConditionalOnExpression("'${spring.ai.zhipuai.api-key:}' == ''")
    public ChatClient reactChatClientOllama(OllamaChatModel chatModel, /* tools */) {
        return buildReactClient(chatModel, /* tools */);   // Ollama 兜底
    }

    @Bean
    public AgentExecutor agentExecutor(@Qualifier("reactChatClient") ChatClient reactChatClient) {
        return new AgentExecutor(reactChatClient);
    }
}
```

要点：
- **两个 bean 同名 `reactChatClient`** + **互斥的 `@ConditionalOnExpression`**（一个判非空、一个判空）→ 保证任意环境**恰好注册一个**。
- 消费方用 `@Qualifier("reactChatClient")` 精确注入，永远能解析。

> 💡 为什么 GLM 当主力？因为 `deepseek-r1` 的 function calling **不稳定**（这正是当初要切 GLM-5.2 的原因）。GLM-5.2 的原生 function calling 稳定可靠，是 Agent 工具调用的更优选。

---

## 七、`AgentExecutor` 为什么这么薄？

这是理解整个设计的关键。我的 `AgentExecutor` 类核心方法只有**一行有效调用**：

```java
public AgentResponse execute(AgentRequest request) {
    // ...
    String answer = chatClient.prompt()
            .user(request.question())
            .call()
            .content();        // ← 就这一句，Thought→Action→Observation 循环全在它内部
    // ...
}
```

类注释说得很直白：

```java
// The actual ReAct (Thought→Action→Observation) loop is NOT hand-rolled here — it runs
// inside Spring AI's ChatModel, enabled by defaultTools(@Tool) on the client. This class
// only drives a single call and wraps its output into reasoning/answer phases for both
// the blocking and the SSE (streaming) response paths.
```

**翻译：真正的 ReAct 循环不是我在这个类里手写的，它跑在 Spring AI 的 ChatModel 内部，由 `defaultTools(@Tool)` 启用。我这个类只负责发起一次调用，把输出包装成 reasoning/answer 两阶段（给前端 SSE 流式用）。**

这反过来说明一个重要原则：**在 Spring AI 里，"手搓 ReAct 循环" 几乎总是错的。** 框架已经把 Thought→Action→Observation 内置在 `ChatModel.call` 里了，你手搓只会重复造轮子，而且——如本文所述——手搓的版本常常不触发执行循环。

---

## 八、验证：3/3 实测通过

切到 `defaultTools(@Tool)` 后，真实 GLM-5.2 实测：

| 测试 | 输入 | 工具 | 结果 |
|---|---|---|---|
| 1 | "今天是几号？" | `getCurrentDateTime` | ✅ 返回**真实当前日期**（不是模型瞎编的） |
| 2 | "算一下 123 × 456" | `calculate` | ✅ 返回 `56088` |
| 3 | 一般问题 | 无需工具 | ✅ 直接回答 |

关键判据：**测试 1 返回的是部署机器的真实日期**，这只能由工具执行产生，证明执行循环真正跑起来了。

---

## 九、避坑速查表

| 坑 | 现象 | 根因 | 修复 |
|---|---|---|---|
| 手搭 `ToolCallAdvisor` | 工具不执行，`tool_calls` 成文本 | 只注册了工具描述，没启用执行循环 | 改用 `defaultTools(@Tool beans)` |
| 工具接自由文本不校验 | RCE / 注入风险 | 信任模型传参 | 白名单正则 + 手写 Parser，禁 `eval` |
| 双模型注入歧义 | 找不到 bean | 两个同类型 ChatClient | 同名 bean + 互斥条件 + `@Qualifier` |
| 手搓 ReAct 循环 | 重复造轮子且易失效 | 不知循环已在框架内 | 用 `defaultTools`，Executor 保持薄 |

---

## 写在最后

这个坑的**隐蔽性极强**：应用不报错、模型不报错、工具描述也正确，只有"工具从未执行"这一个症状，而且模型还会用参数知识**伪造**一个看似合理的答案，让你以为一切正常。

一句话总结：**在 Spring AI 1.1.x，工具调用用 `defaultTools(@Tool beans)`，别手搭 `ToolCallAdvisor` + `ToolCallingManager`。前者启用执行循环，后者没有。**

下一篇我会讲 **Spring AI 1.1.4 实测没有 `RetrievalAugmentationAdvisor`，如何自建 `BaseAdvisor` 实现 Modular RAG**——又一个"官方文档/教程有、实际 jar 包里没有"的 API 幻觉坑。

> 本文代码来自真实项目 `knowledge-assistant`。Java + Spring AI 中文赛道持续更新中。
