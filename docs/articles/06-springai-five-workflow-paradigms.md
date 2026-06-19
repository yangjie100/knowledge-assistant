# Spring AI 工作流设计:Anthropic 五种 Agent 范式的 Java 实现

> 本文基于 knowledge-assistant 项目 `ka-agent` 模块的真实代码,演示如何用 Spring AI + Java 落地 Anthropic 在《Building Effective Agents》中提出的五种工作流范式。代码全部经过编译与运行验证,不是纸上谈兵。

---

## 一、先泼一盆冷水:不是所有任务都需要 Agent

很多人入门 LLM 应用,第一反应是"搞一个 ReAct Agent,让它自己决定调什么工具、循环到满意为止"。这在某些场景下是对的,但在**绝大多数生产场景下是过度设计**。

Anthropic 在 2024 年底那篇被反复引用的《Building Effective Agents》里,把话说得很直白:

> Agents(自主循环的 LLM)在工作流(预先编排好的 LLM 调用序列)之上。**当 LLM 的可靠性足够、任务路径可预测时,优先用工作流;当任务需要灵活的、模型驱动的决策时,才用 Agent。**

这句话翻译成工程语言就是:

- **能确定性编排的,别交给模型的概率决策** —— 每多一次"让模型自己决定",就多一次幻觉、多一次延迟、多一份 token 成本。
- **工作流是可控的、可测试的、可观测的**;Agent 循环是概率性的、难以单测的、容易跑飞的。
- 一人公司场景下,你写不出足够健壮的 Agent 提示词来覆盖所有边角,但你能写出确定性的 `for` 循环。

所以 knowledge-assistant 的 `ka-agent` 模块同时提供了两条路:
- **ReAct Agent**(`AgentExecutor`):处理需要"思考-行动-观察"多轮、需要动态选择工具的复杂问答;
- **工作流**(`workflow/` 包):处理路径可预测的内容生成、客服路由、多视角评审等任务。

本文聚焦后者。我们用 5 种工作流范式中的 3 种建成了完整可运行的实现,另外 2 种基于同一套抽象给出了扩展方案。

---

## 二、Anthropic 五范式总览

Anthropic 定义了五种工作流范式,外加一个"Agent"作为第六种更自主的形态:

| 范式 | 中文 | 核心思想 | ka-agent 状态 |
|---|---|---|---|
| **Prompt Chaining** | 提示链 | 任务拆成串行步骤,前一步输出喂给后一步 | ✅ `ChainWorkflow` |
| **Routing** | 路由 | 先分类输入,再分发到对应处理分支 | ✅ `RoutingWorkflow` |
| **Parallelization** | 并行化 | 多个步骤同输入并发跑,结果合并(sectioning/voting) | ✅ `ParallelizationWorkflow` |
| **Orchestrator-Workers** | 编排者-工作者 | 一个 LLM 编排者动态拆分任务、派给工作者 | 🔧 抽象支持,本文给方案 |
| **Evaluator-Optimizer** | 评估者-优化器 | 生成→评估→不达标则重写,循环收敛 | 🔧 抽象支持,本文给方案 |
| **Agent**(第六种) | 自主智能体 | LLM 自主循环、动态决策、工具调用 | ✅ `AgentExecutor`(另文详述) |

下文先讲贯穿所有范式的抽象设计,再逐个展开。

---

## 三、抽象设计:两个接口撑起全部范式

整个工作流子系统的核心抽象只有两个接口,加起来不到 30 行。

### 3.1 `Workflow` —— 流程入口

```java
public interface Workflow {
    WorkflowResponse execute(WorkflowRequest input);
}
```

极简。一个输入、一个输出。所有范式(链/路由/并行)都是它的不同实现。调用方(`WorkflowController` / `ChatService`)完全不需要知道内部是哪种范式——这正是多态的价值。

### 3.2 `WorkflowStep` —— 最小可执行单元

```java
public interface WorkflowStep {
    String execute(String input);
    String name();
}
```

注意它的签名:**吃 String、吐 String**。这是刻意的设计。

为什么不是泛型 `<T, R>`、不是 `Map<String,Object>`、不是 `WorkflowContext`?因为:

1. **函数式管道的本质就是字符串流过**。上一个步骤的输出文本,原样作为下一个步骤的输入文本。把 LLM 调用想成 Unix 管道:`step1 | step2 | step3`,中间流淌的就是文本。
2. **泛型会在组合时爆炸**。`Step<String,String>` 拼 `Step<String,Map>` 拼 `Step<Map,String>`?类型对不上的地方就要写胶水,范式就失去了"可插拔"特性。
3. **字符串给了最大的灵活性**。LLM 天然产出文本,JSON 解析、结构化提取都可以藏在某个具体 Step 内部,对外仍然是 String 接口。

`name()` 返回步骤名,用于日志和并行结果聚合(后面会看到)。

### 3.3 `ChatClientStep` —— 把 ChatClient 包成 Step

大部分步骤的本质就是"拿一段输入,过一个系统提示词,产出回答"。这个样板被抽到 `ChatClientStep`:

```java
public class ChatClientStep implements WorkflowStep {
    private final ChatClient chatClient;
    private final String stepName;
    private final String systemPrompt;

    public ChatClientStep(ChatClient chatClient, String stepName, String systemPrompt) {
        this.chatClient = chatClient;
        this.stepName = stepName;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String name() { return stepName; }

    @Override
    public String execute(String input) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(input)
                .call()
                .content();
    }
}
```

有了这个,创建一个"文章润色步骤"就是一行:`new ChatClientStep(chatClient, "polish", "优化语言表达和可读性")`。

整个抽象层就这么多。下面看 5 种范式怎么在这个地基上盖楼。

---

## 四、范式一:Prompt Chaining(提示链)

### 4.1 思想

把一个复杂任务拆成若干串行子任务,每个子任务由一次 LLM 调用完成,前一步的输出是后一步的输入。典型例子:写文章 = 拟大纲 → 扩写 → 润色。

关键取舍:
- **中间步骤可以用更小/更便宜的模型**(因为任务更简单);
- **可以在中间插入"门控"**(Gate),比如大纲检查不过就提前失败,不浪费后面的 token;
- **整体延迟 = 各步延迟之和**,所以不适合实时对话,适合离线批处理。

### 4.2 实现:`ChainWorkflow`

```java
@Slf4j
public class ChainWorkflow implements Workflow {
    private final List<WorkflowStep> steps;

    public ChainWorkflow(List<WorkflowStep> steps) {
        this.steps = steps;
    }

    @Override
    public WorkflowResponse execute(WorkflowRequest input) {
        String current = input.question();
        try {
            log.info("Chain workflow started, step count: {}", steps.size());
            for (WorkflowStep step : steps) {
                log.info("Executing step: {}, input length: {}", step.name(), current.length());
                current = step.execute(current);  // 上一步输出 → 下一步输入
            }
            log.info("Chain workflow completed");
            return WorkflowResponse.builder()
                    .content(current)
                    .success(true)
                    .build();
        } catch (Exception e) {
            log.error("Chain workflow failed", e);
            return WorkflowResponse.builder()
                    .success(false)
                    .errorMessage("Chain workflow failed: " + e.getMessage())
                    .build();
        }
    }
}
```

就这么几行。核心是一个 `for` 循环,`current` 变量像接力棒一样在步骤间传递。`try-catch` 保证任意一步抛异常都不会把整个链搞崩,而是优雅返回失败响应。

### 4.3 装配:内容生成链

`AgentWorkflowConfig.buildContentChain()` 把三步串起来:

```java
public Workflow buildContentChain() {
    List<WorkflowStep> steps = List.of(
            createStep("outline", "Generate a detailed outline based on the given topic..."),
            createStep("expand",   "Based on the provided outline, write a complete article."),
            createStep("polish",   "Polish and optimize the provided article...")
    );
    return createChainWorkflow(steps);
}
```

执行 `topic → outline → article → polished-article`,每一步都基于上一步的完整文本。这种"逐稿打磨"的链式,是内容质量明显高于"一步生成"的关键。

### 4.4 RAG 链:工作流也能搞检索增强

提示链不止能串 LLM。`buildRagChain()` 串了两个**异构** Step:

```java
public Workflow buildRagChain() {
    List<WorkflowStep> steps = List.of(
            createSearchStep("knowledge-search"),                      // 检索(非 LLM)
            createContextAnswerStep("rag-answer", "根据检索上下文回答...")  // 生成(LLM)
    );
    return createChainWorkflow(steps);
}
```

`KnowledgeSearchStep` 内部调 `RetrievalService`(向量+BM25+重排),把检索到的上下文拼成文本;`ContextAnswerStep` 拿到这段文本,生成最终回答。两者都满足 `String → String` 契约,所以能无缝串在同一个链里——这正是 String 接口的威力:**不关心 Step 内部是调 LLM、查数据库、还是算数学题,只要契约一致就能组合。**

---

## 五、范式二:Routing(路由)

### 5.1 思想

先对输入做一次分类,再把它分发到最擅长的处理分支。典型例子:客服系统按意图分流到账单/技术/产品/通用;RAG 系统按问题类型决定走知识库还是闲聊。

关键取舍:
- **每个分支可以用不同的提示词、不同的模型、不同的工具**;
- **分类可以由 LLM 做(软路由),也可以由规则做(硬路由)**;
- **路由错误会让整条链跑偏**,所以分类质量是生命线。

### 5.2 实现:`RoutingWorkflow`

```java
@Slf4j
public class RoutingWorkflow implements Workflow {
    private final WorkflowStep router;            // 分类步骤
    private final Map<String, WorkflowStep> stepMap;  // 路由键 → 处理步骤

    public RoutingWorkflow(WorkflowStep router, Map<String, WorkflowStep> stepMap) {
        this.router = router;
        this.stepMap = stepMap;
    }

    @Override
    public WorkflowResponse execute(WorkflowRequest input) {
        try {
            log.info("Routing workflow started, route count: {}", stepMap.size());
            String routeKey = router.execute(input.question()).trim();   // 第 1 次 LLM 调用:分类
            log.info("Route selected: {}", routeKey);

            WorkflowStep step = stepMap.get(routeKey);
            if (step == null) {                                           // 路由键兜底
                log.warn("No step found for route: {}", routeKey);
                return WorkflowResponse.builder()
                        .success(false)
                        .errorMessage("No step found for route: " + routeKey)
                        .build();
            }

            log.info("Executing step: {}", step.name());
            String result = step.execute(input.question());              // 第 2 次 LLM 调用:处理
            return WorkflowResponse.builder().content(result).success(true).build();
        } catch (Exception e) {
            log.error("Routing workflow failed", e);
            return WorkflowResponse.builder().success(false)
                    .errorMessage("Routing workflow failed: " + e.getMessage()).build();
        }
    }
}
```

注意结构:**路由本身也是一个 `WorkflowStep`**(只不返回的是路由键,不是最终答案)。这个设计让路由器和处理步骤共用同一套抽象,`RoutingWorkflow` 不需要任何特例代码。

### 5.3 路由器:`RouterSelectorStep`

路由器要做的就一件事:读输入,吐一个合法的路由键。难点在于"合法"——LLM 经常多吐一个字、带个标点、或者干脆胡说一个不存在的键。`RouterSelectorStep` 用两层防御:

```java
@Override
public String execute(String input) {
    // 1. 动态拼接可用路由清单进提示词(不硬编码,新增路由自动可见)
    String routeInfo = stepMap.entrySet().stream()
            .map(entry -> "- " + entry.getKey() + ": " + entry.getValue().name())
            .collect(Collectors.joining("\n"));
    String systemPrompt = String.format(ROUTER_PROMPT, routeInfo);

    String routeKey = chatClient.prompt()
            .system(systemPrompt).user(input).call().content();

    // 2. 校验返回值是否在合法集合内,不在就返回 null(由上层兜底)
    if (routeKey != null && stepMap.containsKey(routeKey.trim())) {
        return routeKey.trim();
    }
    log.warn("Router returned invalid key: {}", routeKey);
    return null;
}
```

两个工程细节值得展开:

**(a) 提示词里的路由清单是运行时动态拼接的。** 模板用 `%s` 占位:

```java
private static final String ROUTER_PROMPT = """
        You are a professional router selector. Based on the user question,
        select the most appropriate route from the options below:

        Available routes:
        %s

        Return ONLY the route key name, with no extra explanation.
        For example, if the best route is "technical", return just "technical".""";
```

这意味着新增一个路由分支,只需要往 `stepMap` 里 put 一项,提示词自动更新——**不用改任何字符串**。这是"数据驱动配置"在提示工程里的直接体现。

**(b) `containsKey` 兜底是必须的。** LLM 即使被要求"只返回键名",也可能返回 `"technical。"` 或 `"我认为应该走 technical 路由"`。直接拿这种字符串去 `stepMap.get` 一定得到 null。先 `trim` 再 `containsKey` 校验,把"格式正确但语义错"和"格式就错"统一收口成 null,交给 `RoutingWorkflow` 走兜底分支。生产里你还可以再加一层:把 null 路由到一个"通用兜底 Step",而不是直接报错。

### 5.4 装配:客服路由与 RAG 路由

**客服四路由**(每个分支不同提示词,但都用同一个 ChatClient):

```java
public Workflow buildCustomerServiceRouting() {
    Map<String, WorkflowStep> stepMap = new LinkedHashMap<>();  // LinkedHashMap 保序,日志可读
    stepMap.put("billing",   createStep("billing",   "Handle billing inquiries. Answer in Chinese."));
    stepMap.put("technical", createStep("technical", "Handle technical support questions. Answer in Chinese."));
    stepMap.put("product",   createStep("product",   "Handle product information questions. Answer in Chinese."));
    stepMap.put("general",   createStep("general",   "Handle general questions. Answer in Chinese."));
    return createRoutingWorkflow(stepMap);
}
```

**RAG 三路由**(更有意思——`knowledge` 分支本身是一条嵌套链):

```java
public Workflow buildRagRouting() {
    Map<String, WorkflowStep> stepMap = new LinkedHashMap<>();

    List<WorkflowStep> knowledgeSteps = List.of(
            createSearchStep("knowledge-search"),
            createContextAnswerStep("knowledge-answer", "根据检索到的上下文回答知识库问题。")
    );
    stepMap.put("knowledge", new ChainStep("knowledge-chain", knowledgeSteps));  // 嵌套链!
    stepMap.put("chat",  createStep("chat",  "你是一个友好的对话助手..."));
    stepMap.put("coding", createStep("coding", "你是一个编程专家..."));

    return createRoutingWorkflow(stepMap);
}
```

注意 `knowledge` 这个路由值:它是一个 `ChainStep`(把一条链包成单个 Step)。**因为所有范式都实现 `WorkflowStep` 契约,所以范式可以嵌套**——路由的某个分支里跑一条链,链的某一步又可以是并行投票。这就是抽象一致的复利收益。

---

## 六、范式三:Parallelization(并行化)

### 6.1 思想

同一个输入,同时丢给多个步骤跑,结果合并。Anthropic 把它细分为两种:
- **Sectioning(分片)**:把任务切成不重叠的子任务并行(如多视角评审:营销/产品/UX 各写一份);
- **Voting(投票)**:同一任务跑多次,取多数/最优(如代码审查跑 3 遍取共识,降低单次幻觉)。

关键取舍:
- **总延迟 ≈ 最慢那一步的延迟**,而不是各步之和——这是并行相对串行的核心优势;
- **适合"可独立切分"的任务**,不适合有强依赖的;
- **要小心线程池与 token 限流**:并发 N 个步骤 = 瞬时 N 倍的 LLM 请求速率。

### 6.2 实现:`ParallelizationWorkflow`

```java
@Slf4j
public class ParallelizationWorkflow implements Workflow {
    private final List<WorkflowStep> steps;

    public ParallelizationWorkflow(List<WorkflowStep> steps) {
        this.steps = steps;
    }

    @Override
    public WorkflowResponse execute(WorkflowRequest input) {
        try {
            List<CompletableFuture<Map.Entry<String, String>>> futures = new ArrayList<>();

            // 1. 为每个步骤提交一个异步任务(共用 ForkJoinPool.commonPool)
            for (WorkflowStep step : steps) {
                CompletableFuture<Map.Entry<String, String>> future = CompletableFuture.supplyAsync(() -> {
                    log.info("Executing step: {}", step.name());
                    String result = step.execute(input.question());  // 同输入
                    return Map.entry(step.name(), result);
                });
                futures.add(future);
            }

            // 2. 等所有任务完成,聚合成 Map<stepName, result>
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));
            CompletableFuture<Map<String, String>> resultFuture = allFutures.thenApply(v ->
                    futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            Map<String, String> results = resultFuture.get();

            String content = formatResults(results);  // 3. 拼接成最终文本
            return WorkflowResponse.builder().success(true).content(content).build();

        } catch (InterruptedException | ExecutionException e) {
            log.error("Parallel workflow failed", e);
            Thread.currentThread().interrupt();  // 关键:恢复中断状态
            return WorkflowResponse.builder().success(false)
                    .errorMessage("Parallel workflow failed: " + e.getMessage()).build();
        }
    }
}
```

这段有几个**容易被面试/被 Code Review 挑、但每一行都对**的细节:

**(a) 为什么用 `Map.entry(step.name(), result)` 而不是直接 `CompletableFuture<String>`?**
因为并行合并时,我们丢失了"这段结果来自哪个步骤"的信息。把 `name` 和 `result` 打包成 Entry 一起带回来,后续 `formatResults` 才能输出可读的结构化结果。

**(b) `allOf(...).thenApply(...)` 的链式为什么这么写?**
`allOf` 返回的是 `CompletableFuture<Void>`(它只表示"都完成了",不带数据)。`.thenApply` 在"都完成"这个信号触发后,再回去 `join` 每个 future 取结果——此时 join 一定不会阻塞(因为 allOf 已经保证全完成),所以是安全的。这比"手动循环 join"更符合异步编程范式。

**(c) `Thread.currentThread().interrupt()` 这一行为什么不能省?**
这是 Java 并发的经典规范。`resultFuture.get()` 抛 `InterruptedException` 意味着"等待线程被打断了",而捕获异常会**清除线程的中断标志位**。如果你吞掉异常不恢复,上层调用栈(线程池、Web 容器)就感知不到"发生过中断",可能导致线程池关闭、请求超时等逻辑失灵。**捕获 InterruptedException 后,要么继续向上抛,要么调用 `interrupt()` 恢复标志位**——这是《Java 并发实战》第 7 章的铁律。一人公司代码也要守住这条底线,不然某天压测时线程池行为诡异,你排查三天。

### 6.3 装配:多视角产品评审

```java
public Workflow buildReviewParallel() {
    List<WorkflowStep> steps = List.of(
            createStep("marketing", "Evaluate the product from a marketing perspective..."),
            createStep("product",  "Evaluate the product from a product management perspective..."),
            createStep("ux",       "Evaluate the product from a UX perspective...")
    );
    return createParallelWorkflow(steps);
}
```

典型的 Sectioning:同一份产品描述,营销/产品/UX 三个角色并行各出一份评审,最后 `formatResults` 拼成:

```
Parallel workflow results:
Step [marketing]:
...
Step [product]:
...
Step [ux]:
...
```

如果换成 Voting 模式,只要把三个 Step 的提示词换成**相同**(比如都是"审查这段代码的安全问题"),再在 `formatResults` 后加一层"取并集/去重/投票"的合并逻辑即可——`ParallelizationWorkflow` 本身不用改。

---

## 七、范式四:Orchestrator-Workers(编排者-工作者)

> ka-agent 当前**未实现**这一范式。下面给出基于现有抽象的扩展方案,标注为"设计草案",未经运行验证。

### 7.1 它和 Routing 的区别

很多人把 Orchestrator-Workers 和 Routing 搞混。关键区别:

| 维度 | Routing | Orchestrator-Workers |
|---|---|---|
| 分支是预定义的吗? | ✅ 是,`stepMap` 写死 | ❌ 否,编排者**动态**决定要几个工作者、各做什么 |
| 适合的任务 | 分类明确的分发 | 任务结构事先未知(如"研究这个开放课题") |

Anthropic 的例子: coding 任务里"需要改 3 个文件"——这个"3 个文件"是编排者**读题后才知道的**,无法预先在 `stepMap` 里列出来。

### 7.2 Java 扩展方案

基于 `Workflow`/`WorkflowStep` 抽象,可以这么设计:

```java
public class OrchestratorWorkflow implements Workflow {
    private final ChatClient orchestrator;  // 编排者:动态拆任务
    private final Function<String, WorkflowStep> workerFactory;  // 按子任务动态造 Step

    @Override
    public WorkflowResponse execute(WorkflowRequest input) {
        // 1. 编排者:分析输入,输出 JSON 子任务列表
        String planJson = orchestrator.prompt()
                .system("分析任务,拆成若干独立子任务,返回 JSON 数组 [{\"task\":\"...\"},...]")
                .user(input.question()).call().content();

        List<String> subtasks = parseSubtasks(planJson);  // 解析 JSON

        // 2. 为每个子任务动态创建 worker(同 Parallelization 的并发套路)
        List<WorkflowStep> workers = subtasks.stream()
                .map(workerFactory)
                .toList();

        // 3. 复用并行执行 + 合并
        //    (此处可直接组合一个内部 ParallelizationWorkflow)
        ParallelizationWorkflow parallel = new ParallelizationWorkflow(workers);
        WorkflowResponse partial = parallel.execute(input);

        // 4. 编排者再合成最终答案
        String finalAnswer = orchestrator.prompt()
                .system("把以下各子任务结果综合成最终答案")
                .user(partial.getContent()).call().content();

        return WorkflowResponse.builder().content(finalAnswer).success(true).build();
    }
}
```

核心思想:把 `RoutingWorkflow` 的"预定义 stepMap"换成"编排者动态生成 workers + workerFactory"。**由于 `WorkflowStep` 是统一契约,动态生成的 workers 可以直接喂给现成的 `ParallelizationWorkflow`**——范式之间能组合,就是因为抽象一致。

### 7.3 什么时候才需要它

诚实的建议:**一人公司 90% 的场景用不到 Orchestrator-Workers**。它的复杂度(动态解析 JSON、动态构造步骤、再合成)远高于收益。如果你的任务能静态拆,就用 Chain;能静态分类,就用 Routing。只有当你**真的无法事先知道要做几件事**时(比如一个开放式研究 Agent),才上这个范式——而那种场景,你多半本来就该直接用 ReAct Agent 了。

---

## 八、范式五:Evaluator-Optimizer(评估者-优化器)

> 同样未在 ka-agent 实现。下面是扩展方案,标注为"设计草案"。

### 8.1 思想

生成一份草稿 → 用一个"评估者"LLM 打分/挑刺 → 如果不达标,把评估反馈喂回去让"生成者"重写 → 循环直到达标或达上限。典型的"自我修正"循环,适合翻译、安全审查、对格式要求严格的生成任务。

### 8.2 Java 扩展方案

```java
public class EvaluatorOptimizerWorkflow implements Workflow {
    private final WorkflowStep generator;   // 生成者
    private final WorkflowStep evaluator;   // 评估者(返回评分 + 评语)
    private final int maxIterations;        // 防死循环硬上限

    @Override
    public WorkflowResponse execute(WorkflowRequest input) {
        String draft = generator.execute(input.question());

        for (int i = 0; i < maxIterations; i++) {
            String evaluation = evaluator.execute(draft);      // 评估当前草稿
            EvalResult er = EvalResult.parse(evaluation);     // 解析:score + feedback
            if (er.score >= THRESHOLD) {                        // 达标,跳出
                return WorkflowResponse.builder().content(draft).success(true).build();
            }
            // 不达标:把反馈拼进输入,让生成者重写
            String reviseInput = input.question()
                    + "\n\n上一稿:" + draft
                    + "\n评估反馈:" + er.feedback
                    + "\n请据此改进。";
            draft = generator.execute(reviseInput);
        }
        // 达上限仍未达标,返回最后版本 + 警告
        return WorkflowResponse.builder().content(draft).success(true)
                .errorMessage("Reached max iterations").build();
    }
}
```

### 8.3 这个范式和你前面写的 RAG Eval 的关系

如果你读过本系列《用 LLM-as-Judge 给 RAG 打分》那篇,会发现这里的"评估者"和那篇的 judge 是同一个东西——**都是一个 LLM,按 rubric 给输出打分**。区别只在于:

- RAG Eval 的 judge 是**离线、一次性、用于横向比较系统版本**;
- Evaluator-Optimizer 的评估者是**在线、循环、用于驱动单次生成的自我修正**。

所以如果项目里已经有了 `eval/judge.py` 的 rubric 和打分逻辑,完全可以把那套 rubric 移植成 `evaluator` Step 的系统提示词,复用评分标准。这是"评估资产"复用的典型路径。

### 8.4 注意事项

- **`maxIterations` 硬上限必须有**。LLM 评估 LLM,很容易陷入"永远觉得不够好"的死循环,token 烧穿。一人公司设 2-3 轮足够。
- **评估者和生成者最好用不同模型**(避免同一个模型既当运动员又当裁判,容易自我宽容)。
- **阈值 `THRESHOLD` 要基于离线评测校准**,别拍脑袋设 0.9。

---

## 九、关键坑:`@Qualifier` 被 Lombok 吞掉,@Primary 劫持 ChatClient

这是整个工作流模块**最隐蔽、也最值得讲**的一个坑。它不在工作流范式本身,而在 Spring 装配层,但能直接决定你的工作流是 8 秒还是 180 秒。

### 9.1 现象

knowledge-assistant 同时跑了两个 ChatClient:
- `reactChatClient` / `workflowChatClient`(GLM-5.2,云上,快,3-8 秒);
- `@Primary` ChatClient(默认,绑 Ollama 本地 `deepseek-r1:32b`,慢,长答案 180 秒)。

工作流本该用 GLM 那个快的。结果实测 `buildRagChain()` 跑一次要 3 分钟——工作流悄悄用了 Ollama 慢模型。

### 9.2 根因

最初的 `AgentWorkflowConfig` 用了 Lombok:

```java
// 出问题的写法
@RequiredArgsConstructor
@Configuration
public class AgentWorkflowConfig {
    private final @Qualifier("workflowChatClient") ChatClient chatClient;  // ❌
    ...
}
```

看起来没毛病:`@Qualifier` 标在字段上,Lombok 生成的构造器应该带上它……**但 Lombok 默认不会把 `@Qualifier` 复制到生成的构造器参数上**。

要让 Lombok 复制,得在项目里加 `lombok.config`:

```
lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Qualifier
```

而本项目没有这个配置文件。结果是构造器参数上没有 `@Qualifier`,Spring 按类型注入时遇到两个 `ChatClient` bean,就fallback 到 `@Primary` 那个——也就是 Ollama 慢模型。

### 9.3 修复:手写构造器

最终代码改成了手写构造器,把 `@Qualifier` 显式放在参数上:

```java
public class AgentWorkflowConfig {

    private final ChatClient chatClient;
    private final RetrievalService retrievalService;

    /**
     * Injects the workflow-dedicated ChatClient by qualifier, NOT the @Primary chatClient
     * (which binds Ollama deepseek-r1 and took ~180s for long Chinese answers).
     * Hand-written constructor instead of @RequiredArgsConstructor:
     * Lombok does not copy @Qualifier onto generated constructor params (no lombok.config
     * copyableAnnotations entry in this repo), so the qualifier would be silently dropped
     * and @Primary chatClient injected instead.
     */
    public AgentWorkflowConfig(@Qualifier("workflowChatClient") ChatClient chatClient,
                               RetrievalService retrievalService) {
        this.chatClient = chatClient;
        this.retrievalService = retrievalService;
    }
    ...
}
```

### 9.4 教训

1. **`@Qualifier` + `@RequiredArgsConstructor` 是一个隐性陷阱**。看起来能编译、能启动、甚至能跑——只是跑得慢/跑得错,而你不一定立刻发现。这种"静默退化"是最难查的 bug。
2. **`@Primary` 是双刃剑**。它解决了"多 bean 时默认注入谁"的问题,但同时**掩盖了"你本想注入另一个"的错误**。一旦某个本该用 `@Qualifier` 的地方 qualifier 丢了,fallback 到 primary 不会报错,只会性能崩。
3. **解法二选一**:要么加 `lombok.config` 让 Lombok 复制 `@Qualifier`(全局生效,但要记得维护这个文件);要么对需要 qualifier 的类**手写构造器**(局部、显式、一眼能看出来)。本项目选了后者,因为只有少数配置类需要。

> 这个坑的排查过程本身就是一个完整的"性能问题诊断"案例:从"工作流慢"的症状,到"用错模型"的根因,再到"Lombok 吞注解"的真因。建议在自己的 Spring AI 多模型项目里,**启动时打印实际注入的 ChatClient 类型**(本项目 `@PostConstruct logInit()` 就在干这事),让这类静默问题第一时间暴露。

---

## 十、何时不该用工作流(Anthropic 的忠告)

讲了这么多工作流的好,必须把 Anthropic 那句反过来说:**别把简单问题复杂化**。

他们的原话大意是:很多团队投入大量精力搞 Agent 编排,结果发现把提示词写好、把任务拆直接一点,效果就够好了。**先从最简单的方案开始,只有当简单方案确实不够时才增加复杂度。**

落到 knowledge-assistant 的决策表:

| 任务特征 | 推荐方案 |
|---|---|
| 一次性问答,无上下文依赖 | 单次 `ChatClient.call()`,别用工作流 |
| 多轮对话,需要记忆 | `ChatClient` + `MessageChatMemoryAdvisor`,别用工作流 |
| 任务可静态拆成固定步骤 | **Prompt Chaining** |
| 输入可静态分类成固定分支 | **Routing** |
| 同输入要多视角/多次投票 | **Parallelization** |
| 子任务数量事先未知 | Orchestrator-Workers(或直接 ReAct Agent) |
| 需要自我修正收敛 | Evaluator-Optimizer |
| 路径完全不可预测、要动态选工具 | **ReAct Agent**(`AgentExecutor`) |

一个朴素的判断标准:**你能不能在纸上画出任务的执行流程图?** 画得出(哪怕有分支),就是工作流;画不出、必须让模型边走边决定,才是 Agent。knowledge-assistant 同时保留了两者,正是为了让"能用工作流就别上 Agent"成为可执行的工程选择。

---

## 十一、决策树与总结

把上面的选择逻辑画成一张决策树(你可以直接贴进团队 Wiki):

```
任务需要 LLM 吗?
├─ 否 → 别用,写普通代码
└─ 是 → 任务路径可预测吗?(能在纸上画出流程图?)
        ├─ 否 → ReAct Agent(动态决策、工具调用)
        └─ 是 → 有几个步骤?
                ├─ 1 个 → 直接 ChatClient.call()
                └─ 多个 → 步骤间关系?
                        ├─ 串行依赖 → Prompt Chaining
                        ├─ 静态分类分发 → Routing
                        ├─ 同输入并发 → Parallelization
                        ├─ 动态拆分 → Orchestrator-Workers
                        └─ 需要自我修正 → Evaluator-Optimizer
```

### 本文要点回顾

1. **工作流优先于 Agent**:路径可预测就别让模型做概率决策,确定性编排更可控、可测、可观测。
2. **两个接口撑起全部范式**:`Workflow`(入口) + `WorkflowStep`(`String→String` 的最小单元)。String 契约让范式可任意嵌套组合。
3. **三种范式已落地**:Chain(串行接力)、Routing(分类分发,含双层兜底)、Parallelization(并发 + 中断规范)。
4. **两种范式给方案**:Orchestrator-Workers(动态拆分,复用 Parallelization)和 Evaluator-Optimizer(自我修正循环,复用 RAG Eval 的 judge)。
5. **Lombok 吞 `@Qualifier` 是隐蔽坑**:多模型项目里,`@RequiredArgsConstructor` + 字段 `@Qualifier` 会被 `@Primary` 静默劫持;要么手写构造器,要么配 `lombok.config`。
6. **能用简单的就别复杂化**:先从单次调用开始,简单方案不够时才上工作流;工作流不够时才上 Agent。

### 下一篇预告

本系列下一篇将转向 **B 赛道**——开始 ka 项目的 P2 进阶开发,第一步是把 `ka-agent` 里现有的 `@Tool` beans(`searchTool`/`dateTimeTool`/`statsTool`/`calcTool`)暴露为一个独立的 **MCP Server**,让其他 MCP 客户端(包括 Claude Code 本身)能复用这套工具。我们会处理 webmvc vs webflux 的 starter 冲突、Streamable HTTP 传输、以及工具契约如何在"直接 Agent 调用"和"MCP 远程暴露"两种模式下保持单一实现源。那是从"写文章"到"造产品"的切换点。

---

*全文基于 knowledge-assistant `ka-agent` 模块真实代码(2026-06 核实)。代码版本:Spring AI 1.1.4 / Spring Boot 3.3.6 / Java 21。Orchestrator-Workers 与 Evaluator-Optimizer 部分为设计草案,未经运行验证,已在文中明确标注。*
