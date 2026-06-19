# Spring AI MCP Server 实战:把 @Tool 暴露给 Claude Code 的 3 个坑与完整方案

> 本文基于 knowledge-assistant 项目真实落地的 `ka-mcp` 模块(已通过编译 + 启动 + 完整 MCP 协议握手 + 4 工具端到端调用验证)。所有代码、报错、返回值均为实测产出,不是文档搬运。

## TL;DR

- **目标**:把一个已有 Spring AI 应用里写好的 `@Tool` 方法,零成本暴露成 MCP Server,让 Claude Code、Cursor 这些 MCP 客户端能直接调用。
- **方案**:独立 `ka-mcp` 模块 + `spring-ai-starter-mcp-server-webmvc` + 一个 `MethodToolCallbackProvider` Bean。4 个文件,核心代码不到 10 行。
- **3 个真实踩坑**(本文核心价值):
  1. **starter 选错直接启动失败** —— webflux starter 和 servlet(Tomcat)不兼容,必须用 webmvc starter。
  2. **1.x 没有 `ToolCallbacks.from(...)`** —— 网上多数示例用的这个 API 在 Spring AI 1.0/1.1 的顶层 tool 包里不存在,要用 `MethodToolCallbackProvider.builder().toolObjects(...)`。
  3. **embedding 维度不对齐索引,MCP 握手能过但 RAG 工具静默失效** —— 协议层全绿,真正调 `searchKnowledge` 才暴露 `vector blob size 3072 does not match 4096`。
- **结论**:MCP Server 本身极其简单,真正耗时的是"让复用的工具在 MCP 上下文里真的能跑"——尤其是 RAG 这类带外部依赖的工具。

---

## 一、为什么要把 Spring AI 应用做成 MCP Server

先回答一个前置问题:**我已经有 HTTP API(`/api/agent/react`、`/rag-chain`)了,为什么还要包一层 MCP?**

答案在于**消费端的差异**:

| 维度 | 自己的 HTTP API | MCP Server |
|------|----------------|------------|
| 谁来调用 | 你写的客户端代码 / 前端 | **AI 编码助手**(Claude Code、Cursor、Windsurf)直接调 |
| 调用决策 | 硬编码在业务流程里 | **模型自己决定**何时调、传什么参数 |
| 接入成本 | 每个客户端写适配 | 客户端配置一行 `url`,工具自动出现 |
| 生态 | 私有 | 标准 MCP 协议,任何 MCP 客户端通吃 |

举个具体场景:knowledge-assistant 里有个 `searchKnowledge(query)` 工具,做知识库语义检索。做成 MCP Server 后,你在 Claude Code 里直接问"这个项目的检索是怎么做的",Claude 会**自动**调用 `searchKnowledge` 去知识库捞真实文档,而不是凭训练数据瞎编。这就是 MCP 的价值:**把你的私有能力,变成 AI 助手的一等公民工具**。

而 Spring AI 的妙处在于:你为应用内 Agent 写的 `@Tool` 方法,和 MCP Server 暴露的工具,**是同一份代码**。不需要重写一遍工具逻辑,只是换一个"装配 + 暴露"的外壳。

---

## 二、架构决策:独立模块 vs 嵌入主应用

知识库助手原本有个 `ka-webapp` 模块(端口 8080,跑 Tomcat,提供 HTTP API + 前端)。MCP Server 有两个落点:

**方案 A:嵌入 ka-webapp**
- 在 ka-webapp 里加 MCP server 依赖 + 配置,复用同一个 Spring 上下文。
- 优点:不用新模块,共享所有 bean。
- 缺点:MCP server 和 Web 应用耦合,部署必须一起;MCP 的长连接 SSE 会占用 webapp 的 Tomcat 线程池;后续想单独给 MCP 加鉴权/限流很别扭。

**方案 B:独立 ka-mcp 模块**(本文选择)
- 新建 `ka-mcp` 模块,依赖 `ka-agent`(工具的源头),独立端口(8090),独立部署。
- 优点:关注点分离;可独立扩缩容;MCP 的安全策略、超时、线程池与主应用隔离;想停 MCP 不影响主站。
- 缺点:多一个模块、多一个进程。

> **决策**:选 B。理由是 MCP Server 的运维属性(无鉴权风险、长连接、外部可调用)和主 Web 应用差异太大,混在一起会互相绑架。一人公司虽然人力有限,但"模块边界清晰"省下的后续心智负担,远大于多建一个模块的成本。

模块依赖关系:

```
ka-mcp  ──depends on──▶  ka-agent  ──depends on──▶  ka-rag / ka-chat / ka-common
   │
   └──depends on──▶  spring-ai-starter-mcp-server-webmvc
```

注意:**ka-mcp 只依赖 ka-agent,不依赖 ka-webapp**。这是关键——保证 MCP 模块不会把 Web 控制器栈拉进来(否则组件扫描会装配一堆不该有的东西)。

---

## 三、4 个文件实现

### 3.1 `pom.xml`:依赖只有两个

```xml
<parent>
    <groupId>com.knowledge</groupId>
    <artifactId>knowledge-assistant</artifactId>
    <version>1.0.0</version>
    <relativePath>../pom.xml</relativePath>
</parent>
<artifactId>ka-mcp</artifactId>

<dependencies>
    <!-- 工具的唯一真实来源:复用 ka-agent 的 @Tool bean,不重写 -->
    <dependency>
        <groupId>com.knowledge</groupId>
        <artifactId>ka-agent</artifactId>
        <version>${project.version}</version>
    </dependency>
    <!-- MCP Server,Spring MVC(Servlet / SSE)transport。
         与全局 starter-web 同属 Servlet 栈,不与 webflux 冲突。
         Spring AI 官方:当 starter-web 存在时用 webmvc starter(非 webflux)。-->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

两个依赖 + 一个打包插件,没了。`spring-boot-maven-plugin` 继承自父 POM,默认会执行 `repackage` 生成可执行 fat jar(后面启动要用)。

### 3.2 `KaMcpApplication.java`:启动类 + 组件扫描

```java
@SpringBootApplication(scanBasePackages = "com.knowledge.assistant")
public class KaMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(KaMcpApplication.class, args);
    }
}
```

**关键点是 `scanBasePackages = "com.knowledge.assistant"`**。

ka-mcp 自己的包是 `com.knowledge.assistant.mcp`,但 `@Tool` bean 在 `ka-agent` 的 `com.knowledge.assistant.agent.tools` 包下,RAG 服务在 `ka-rag` 的 `com.knowledge.assistant.rag.service` 下。如果把扫描范围限定在 `.mcp`,这些 bean 扫不到,`searchKnowledge` 调用时 `RetrievalService` 就是 null。

把扫描根设到 `com.knowledge.assistant`,整个项目的组件都能被装配。这里有个**前提**:ka-mcp 的 pom 没有依赖 ka-webapp/ka-admin,所以 classpath 里根本没有那些模块的类,扫描范围再宽也扫不到它们——安全的。

> 提示:如果哪天给 ka-mcp 加了 ka-webapp 依赖(比如想复用某个 Controller),`com.knowledge.assistant` 扫描就会把整个 Web 控制器栈拉进 MCP 进程。所以约定:**ka-mcp 只能依赖 ka-agent 及其下游,禁止依赖 ka-webapp/ka-admin**。

### 3.3 `McpToolConfig.java`:核心,只有 10 行

```java
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider kaMcpTools(DateTimeTool dateTimeTool,
                                           CalculatorTool calculatorTool,
                                           KnowledgeStatsTool statsTool,
                                           KnowledgeSearchTool searchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(dateTimeTool, calculatorTool, statsTool, searchTool)
                .build();
    }
}
```

**这一个 Bean 就是 MCP Server 的全部魔法**。

工作原理:
1. `dateTimeTool`/`calculatorTool`/`statsTool`/`searchTool` 是 `ka-agent` 里的 `@Component`,每个类上有 `@Tool` 注解的方法。
2. `MethodToolCallbackProvider.builder().toolObjects(...)` 反射读取这些 bean 的 `@Tool` 方法,生成 `ToolCallback` 列表。
3. 返回的 `ToolCallbackProvider` Bean 被 **MCP server 自动配置**自动发现,把每个 callback 转成 MCP 工具规格(name/description/inputSchema),通过 SSE 暴露。
4. MCP 客户端 `tools/list` 时,拿到这 4 个工具;`tools/call` 时,框架反序列化参数、调用对应方法、把返回值包成 MCP 响应。

注入具体 bean 类型(而不是 `List<Object>`),是为了**显式声明暴露哪些工具**——避免某个内部 `@Tool` bean 意外被暴露出去(安全可控)。

> 关于为什么用 `MethodToolCallbackProvider` 而不是网上常见的 `ToolCallbacks.from(...)`:见**第五节坑 2**。

### 3.4 `application.yml`:复用 RAG 栈

```yaml
server:
  port: 8090   # 避开 ka-webapp 8080 和 8081(Docker Desktop 占用)

spring:
  application:
    name: ka-mcp-server
  ai:
    model:
      embedding: ollama          # ⚠️ 见坑 3:这个默认值在 1024d 索引下会出问题
    ollama:
      base-url: http://localhost:11434
      embedding:
        options:
          model: nomic-embed-text
    zhipuai:
      base-url: ${ZHIPU_BASE_URL:https://open.bigmodel.cn/api/coding/paas}
      api-key: ${ZHIPU_API_KEY:}
      chat:
        options:
          model: ${ZHIPU_MODEL:glm-5.2}
    vectorstore:
      redis:
        initialize-schema: true
        index-name: knowledge_assistant
        prefix: "ka_"
    mcp:
      server:
        name: ka-mcp-server
        version: 1.0.0
        type: SYNC
        capabilities:
          tool: true

ka:
  embedding:
    provider: ${KA_EMBEDDING_PROVIDER:ollama}   # ⚠️ 见坑 3
    tei:
      endpoint: ${KA_EMBEDDING_TEI_ENDPOINT:http://localhost:8084}
```

MCP 相关的只有 `spring.ai.mcp.server` 那 5 行。其余全是**为了让 `searchKnowledge` 能走 RAG**——因为它内部调用 `RetrievalService`,需要 embedding 模型 + Redis 向量库。这部分配置和 `ka-webapp` 完全一致(同一套 Redis/GLM/Ollama 栈)。

`capabilities.tool: true` 声明这个 server 支持工具能力(相对的还有 resources、prompts,MCP 协议的三类能力)。`type: SYNC` 匹配 webmvc 的 Servlet 模型。

---

## 四、完整 MCP 协议握手验证

光启动不报错不算数。MCP Server 有个经典坑:**启动成功但客户端死活连不上**(配置错了 SSE 端点、type 选错、capabilities 没声明等)。所以必须做完整握手验证。

MCP SSE transport 的握手流程:

```
客户端                                    服务端(ka-mcp)
  │                                          │
  │─── GET /sse ────────────────────────────▶│  建立 SSE 长连接
  │◀── event: endpoint ─────────────────────│  告诉客户端消息端点
  │◀── data: /mcp/message?sessionId=xxx ────│  + 分配 sessionId
  │                                          │
  │─── POST /mcp/message?sessionId=xxx ─────▶│  initialize
  │     {method:"initialize",...}            │
  │◀── event: message (SSE 流) ──────────────│  返回 serverInfo + capabilities
  │                                          │
  │─── POST notifications/initialized ──────▶│  确认
  │                                          │
  │─── POST tools/list ─────────────────────▶│
  │◀── 返回工具列表 ──────────────────────────│
  │                                          │
  │─── POST tools/call {name,...} ───────────▶│  实际调用
  │◀── 返回执行结果 ──────────────────────────│
```

### 4.1 SSE 握手

```bash
$ curl -N http://localhost:8090/sse
event:endpoint
data:/mcp/message?sessionId=55fed3c0-b600-4059-a5a0-566484cb0c0c
```

拿到 `sessionId`,握手成功。

### 4.2 initialize

```bash
$ curl -X POST "http://localhost:8090/mcp/message?sessionId=55fed3c0-..." \
       -H "Content-Type: application/json" \
       -d '{"jsonrpc":"2.0","id":1,"method":"initialize",
            "params":{"protocolVersion":"2024-11-05","capabilities":{},
                      "clientInfo":{"name":"probe","version":"1.0"}}}'
# HTTP 200,SSE 流返回:
{"jsonrpc":"2.0","id":1,"result":{
  "protocolVersion":"2024-11-05",
  "capabilities":{"tools":{"listChanged":true},"resources":{...},"prompts":{...}},
  "serverInfo":{"name":"ka-mcp-server","version":"1.0.0"}
}}
```

`protocolVersion` 匹配、`serverInfo` 正确、`capabilities.tools` 声明了——协议层握手通过。

### 4.3 tools/list:4 个工具全暴露

```bash
$ curl -X POST ".../mcp/message?sessionId=..." -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
# 返回:
{"tools":[
  {"name":"getCurrentDateTime","description":"Get the current date and time...","inputSchema":{"type":"object","properties":{}}},
  {"name":"getStats","description":"Get current system status...","inputSchema":{...}},
  {"name":"searchKnowledge","description":"Search the knowledge base...","inputSchema":{"properties":{"query":{"type":"string"}},"required":["query"]}},
  {"name":"calculate","description":"Evaluate a mathematical expression...","inputSchema":{"properties":{"expression":{"type":"string"}},"required":["expression"]}}
]}
```

`@Tool` 注解的 `description` 和参数,被框架自动转成了标准 JSON Schema。MCP 客户端靠这个 schema 知道每个工具怎么调。

### 4.4 tools/call:4 个工具真实执行(端到端)

这一步最关键——前面三步只证明"工具能被看见",这步证明"工具真的能跑"。

```bash
# getCurrentDateTime → 返回正确日期
{"id":10,"result":{"content":[{"type":"text","text":"\"2026-06-19 14:29:58 (星期五)\""}],"isError":false}}

# calculate(2+3*4) → 先乘后加 = 14
{"id":11,"result":{"content":[{"type":"text","text":"14"}],"isError":false}}

# getStats → 系统在线
{"id":12,"result":{"content":[{"type":"text","text":"\"Knowledge Assistant Status: Online\nTimestamp: 2026-06-19T14:29:59...\""}],"isError":false}}

# searchKnowledge("系统架构") → ❌ 第一次失败!
{"id":13,"result":{"content":[{"type":"text",
  "text":"Error parsing vector similarity query: query vector blob size (3072) does not match index's expected size (4096)."
}],"isError":true}}
```

前 3 个完美。`searchKnowledge` 翻车了——这就是**坑 3**的现场。下面专门讲。

---

## 五、三大踩坑实录

### 坑 1:webflux starter 与 Tomcat 冲突(选错 starter 启动失败)

**现象**:如果依赖写成 `spring-ai-starter-mcp-server-webflux`(很多教程默认用这个),应用启动时 Tomcat 和 Netty 打架,或者 MCP 端点根本不注册。

**根因**:Spring AI 提供两个 MCP server starter:
- `spring-ai-starter-mcp-server-webmvc`:基于 Spring MVC(Servlet / Tomcat),SSE 通过 Spring MVC 实现。
- `spring-ai-starter-mcp-server-webflux`:基于 WebFlux(Netty,响应式)。

**这俩不能共存**。如果你的项目(或父 POM)已经有 `spring-boot-starter-web`(Tomcat),再引入 webflux starter,会同时把 Servlet 和 Reactive 两套 Web 栈拉进来,Spring Boot 的 Web 应用类型推断会混乱,MCP 自动配置可能不生效或启动报错。Spring AI 官方 issue #4055 记录了这个问题。

**解决**:
- 主应用是 Tomcat(绝大多数 Spring Boot 应用)→ 用 **webmvc** starter。
- 主应用本来就是 WebFlux 响应式 → 用 webflux starter。

knowledge-assistant 的父 POM 有 `spring-boot-starter-web`,所以 ka-mcp 必须用 webmvc starter。这条也是为什么选"独立模块"——如果嵌进 ka-webapp,要小心别把 webflux 拉进来污染。

**决策口诀**:
```
你的 Web 栈是 Servlet(Tomcat)?  → webmvc starter
你的 Web 栈是 Reactive(Netty)?  → webflux starter
两者都不能选错,选错启动就跪。
```

### 坑 2:Spring AI 1.x 没有 `ToolCallbacks.from(...)`(API 不存在)

**现象**:网上很多 Spring AI MCP 示例,装配 ToolCallbackProvider 用的是:

```java
// 网上示例常见写法 —— 但在 1.0/1.1 编译不过!
return ToolCallbacks.from(dateTimeTool, calculatorTool, statsTool, searchTool);
```

编译报错:`cannot find symbol: class ToolCallbacks`。

**根因**:Spring AI 的 tool 包结构在版本间变动过。`ToolCallbacks` 这个工具类在某些版本/某些包路径下存在,但在 **1.0.0 / 1.1.x 的顶层 tool 包(`org.springframework.ai.tool`)里并不存在**。我 `javap` 反编译本地实际解析的 `spring-ai-model` jar,确认顶层 tool 包只有 `ToolCallback`、`ToolCallbackProvider`、`StaticToolCallbackProvider`,没有 `ToolCallbacks`。

**解决**:用 `MethodToolCallbackProvider`,它稳定存在于 1.0.x 和 1.1.x:

```java
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

return MethodToolCallbackProvider.builder()
        .toolObjects(dateTimeTool, calculatorTool, statsTool, searchTool)
        .build();
```

`MethodToolCallbackProvider.builder().toolObjects(Object...)` 反射读取 `@Tool` 方法,实现 `ToolCallbackProvider` 接口。这是跨 1.0.x/1.1.x 都稳定的标准写法。

**教训**:Spring AI 还在快速迭代(1.x → 2.x),API 变动频繁。**网上的示例代码一定要对着自己实际依赖的版本验证**,别直接抄。遇到"找不到符号",第一反应是 `jar tf` + `javap` 反编译看实际 jar 里到底有什么,而不是怀疑自己写错了。

> 顺带一个版本踩坑:本项目 pom 声明 `spring-ai.version=1.1.4`,但排查时一度怀疑本地 `.m2` 只解析到 1.0.0(因为最初 `jar tf` 看到了 1.0.0 目录)。后来用 `mvn help:evaluate` 核实,`.m2` 里 1.0.0 / 1.1.4 / 2.0.0-SNAPSHOT 三个版本并存,Maven 按 BOM 正确解析到 1.1.4。**之前是查 jar 路径查错了目录的误判**。结论:版本问题用 Maven 的 dependency 插件核实,别靠肉眼翻 `.m2` 目录。

### 坑 3:embedding 维度不对齐索引(MCP 握手过,但 RAG 工具静默失效)

这是**最隐蔽、最值得讲**的一个坑。前两个坑编译/启动阶段就暴露,这个坑要等真正调用 `searchKnowledge` 才现形。

**现象**:MCP 完整握手(initialize / tools/list / tools/call)全部 HTTP 200,前 3 个工具(getCurrentDateTime / calculate / getStats)执行正确。唯独 `searchKnowledge` 返回:

```
isError: true
text: "Error parsing vector similarity query: query vector blob size (3072)
       does not match index's expected size (4096)."
```

**根因分析**:

Redis 向量索引要求**查询向量的维度 = 索引建立时的维度**。报错里的数字:
- `query vector blob size (3072)` = 3072 字节 ÷ 4 字节/float = **768 维**(nomic-embed-text 的维度)
- `index's expected size (4096)` = 4096 字节 ÷ 4 = **1024 维**(bge-m3 的维度)

也就是说:
- ka-mcp 当前用 **ollama nomic-embed-text(768d)** 把 query 转成向量;
- 但 Redis 里的 `knowledge_assistant` 索引是按 **bge-m3(1024d)** 建的。

768d 的查询向量塞进 1024d 的索引,Redis 直接拒绝。

**为什么会产生这个错配?**

knowledge-assistant 最近做了一次 embedding 模型切换(`git log` 显示 commit `18740a1: 换 bge-m3 1024d 中文 embedding`)。主应用 `ka-webapp` 改用了 TEI 部署的 bge-m3,并把 Redis 索引按 1024d 重建。但**我创建 ka-mcp 时,`application.yml` 是从老的配置拷的,默认还是 `provider: ollama`(nomic 768d)**,没跟上这次切换。

于是出现了诡异的一幕:MCP Server 一切正常,RAG 工具也能被调用、也能跑 embedding、也能查 Redis,**唯独维度对不上,静默返回 isError**。

**解决**:ka-mcp 的 embedding 必须对齐索引维度。本项目索引用 bge-m3(1024d),所以 ka-mcp 启动时设:

```bash
export KA_EMBEDDING_PROVIDER=tei
export KA_EMBEDDING_TEI_ENDPOINT=http://localhost:8084
java -jar ka-mcp/target/ka-mcp-1.0.0.jar
```

`provider=tei` 激活 `TeiEmbeddingConfig`,提供 bge-m3 1024d 的 EmbeddingModel,和索引对齐。

重新启动后重测:

```
isError: false
len: 12608
content: "# Knowledge Assistant 设计文档 > 通用可定制的智能客服/问答系统,
         基于 Spring AI + Ollama + Redis VectorStore ## 技术栈 Spring Boot 3.3.6 ..."
```

`searchKnowledge("系统架构")` 返回了 12608 字符的真实知识库文档,完整 RAG 链路打通。

**附带踩坑**:排查时还发现 TEI 容器(`tei-embedder`)其实已经停了(`docker ps -a` 显示 `Exited 41 hours ago`)。所以要分两步修:
1. `docker start tei-embedder`(bge-m3 模型加载约 99 秒)
2. ka-mcp 设 `KA_EMBEDDING_PROVIDER=tei` 重启

**这个坑的深层教训**:

> **MCP Server 复用主应用的工具时,工具依赖的外部状态(embedding 模型版本、索引维度、缓存键)必须和主应用完全一致。** 协议层(MCP 握手)验证的是"工具能不能被调用",验证不了"工具依赖的数据契约对不对"。RAG 这类有状态的工具尤其危险——维度对不上不会报启动错,只在运行时静默失败。

**排查 checklist**(RAG 工具在 MCP 下失效时):
1. embedding 模型在线吗?(TEI / Ollama 容器在跑?)
2. embedding 维度 = 索引维度吗?(`FT.INFO <index>` 看 `dim`,对比 embedding 模型维度)
3. 索引里有数据吗?(别假设索引非空)
4. 客户端发的 query 编码对吗?(见下)

**额外的小坑:curl 自测中文 query**

用 `curl -d` 发中文 query 做 MCP 自测时,`searchKnowledge` 返回 "No relevant documents found"。这不是 ka-mcp 的 bug,是 **Git Bash 的 curl 把命令行中文按 GBK 编码**了,实际发给服务端的是乱码,embedding 出来自然匹配不到。解决:用 `--data-binary @utf8file` 从 UTF-8 文件读 body。

> 真正的 MCP 客户端(Claude Code / Cursor)发 JSON 都是 UTF-8,不会有这个问题。这个坑只影响你用 curl 手动自测。

---

## 六、接入 Claude Code 与 Cursor

ka-mcp 跑起来后,接入客户端是 MCP 协议最爽的部分——**配置一行,工具自动出现**。

### Claude Code

**项目级配置**(仓库根 `.mcp.json`,团队共享):

```json
{
  "mcpServers": {
    "ka-mcp": {
      "type": "sse",
      "url": "http://localhost:8090/sse"
    }
  }
}
```

或命令行注册:
```bash
claude mcp add ka-mcp --transport sse http://localhost:8090/sse
```

重启 Claude Code,`/mcp` 命令可见 `ka-mcp` 已连接。之后在对话里问"这个项目的知识库检索怎么实现的",Claude 会自动决定调用 `searchKnowledge` 去捞真实文档,而不是凭记忆编。

### Cursor

`~/.cursor/mcp.json`:
```json
{
  "mcpServers": {
    "ka-mcp": { "url": "http://localhost:8090/sse" }
  }
}
```

Cursor 的 Composer / Chat 同样能直接用这些工具。

**这就是 MCP 的杠杆**:你写一次 `@Tool`,通过 MCP Server,所有支持 MCP 的 AI 客户端(Claude Code、Cursor、Windsurf、Cline……)都能用上你的私有能力。不需要为每个客户端写适配。

---

## 七、安全加固(无鉴权,生产必须处理)

`spring-ai-starter-mcp-server-webmvc` 在 Spring AI 1.x **不内置鉴权**——`/sse` 和 `/mcp/message` 是裸奔的。任何能访问 8090 端口的客户端,都能调用全部 4 个工具,包括:
- `searchKnowledge`:**读取知识库全文**(未授权的数据泄露)
- 间接消耗 GLM API 额度(embedding / 生成计费)

本地开发(localhost)可接受。**生产或内网部署前必须加固**,按强度递增三选一:

| 方案 | 做法 | 适用 |
|------|------|------|
| 网络隔离(最轻) | `server.address: 127.0.0.1` 仅本机,或防火墙限来源 IP | 单机 / 受信内网 |
| 反向代理鉴权 | 前置 Nginx 加 Basic Auth / Bearer Token / mTLS | 跨网部署 |
| 应用层拦截 | 自定义 `WebMvcConfigurer` 拦截 `/sse**` `/mcp/message**` 校验 Token | 不想加反代 |

另外建议补:
- SSE 长连接超时(`spring.mvc.async.request-timeout`)和 Tomcat 连接上限,防止恶意客户端大量开 SSE 连接耗尽线程池(DoS)。
- 调用审计日志(来源 + tool 名 + 时间),便于事后追溯。
- 限流(Bucket4j / Resilience4j),按 IP 限 `/sse` 连接数。

> 安全审查的结论是:ka-mcp 代码本身安全基线达标(密钥全 `${}` 环境变量占位符、Calculator 白名单防注入、RAG 无 SSRF、无敏感日志),**唯一缺口就是端点无鉴权**——这是框架现状,不是代码问题,靠部署侧加固。

---

## 八、总结

### 做对了什么

1. **工具零成本复用**:ka-mcp 没有重写任何工具逻辑,4 个 `@Tool` 全来自 ka-agent,通过一个 `MethodToolCallbackProvider` Bean 暴露。Spring AI 的 `@Tool` 抽象在"应用内 Agent 调用"和"MCP 对外暴露"两个场景下是同一份代码,这是最大的杠杆。
2. **独立模块**:关注点分离,MCP 的安全/超时/线程池与主 Web 应用隔离,后续运维干净。
3. **完整握手验证**:不只看"启动成功",而是 initialize → tools/list → tools/call 全跑一遍,4 个工具逐一真实调用——这才能抓住坑 3 这种"静默失效"。

### 3 个坑的价值排序

| 坑 | 暴露阶段 | 隐蔽度 | 教训 |
|----|---------|--------|------|
| webflux/webmvc starter | 启动 | 低(立刻失败) | 选 starter 要看 Web 栈类型 |
| `ToolCallbacks.from` 不存在 | 编译 | 中(抄网上代码踩) | API 对照实际 jar 验证,别盲抄 |
| embedding 维度不对齐 | **运行时调用** | **高(握手全绿却失效)** | 复用工具要保证外部数据契约一致 |

坑 3 是本文最值钱的部分:**协议握手通过 ≠ 工具真能用**。任何带外部依赖(向量库、模型、缓存)的工具,都必须做真实 `tools/call` 验证,不能止步于 `tools/list`。

### 适合谁

- 已经有 Spring AI 应用、想让 Claude Code/Cursor 调用自己的工具能力 → 直接抄 `McpToolConfig` 那 10 行 + webmvc starter。
- 想把私有知识库 / 私有 API 暴露给 AI 编码助手 → MCP 是当前最标准的做法。
- 踩过"启动成功但连不上"或"工具能看见但不能用"的坑 → 本文的验证流程和排查 checklist 直接可用。

### 扩展方向

- **加 resources / prompts 能力**:MCP 不只有 tools,还能暴露资源(文档/文件)和提示模板。ka-mcp 目前只开了 `capabilities.tool`。
- **Streamable HTTP transport**:SSE transport 在新 MCP 规范里已弃用,2.0 默认 Streamable HTTP。当前 1.x 还用 SSE,升级 2.x 时要切。
- **鉴权**:框架不加,自己加拦截器或反代(见第七节)。
- **工具扩展**:在 ka-agent 加新 `@Tool` bean,ka-mcp 只需在 `toolObjects(...)` 多传一个参数——这就是"工具单一真实来源"的好处。

---

*本文所有代码、报错、返回值来自 knowledge-assistant 项目 `ka-mcp` 模块真实落地(commit `f5b5e6b`),实测日期 2026-06-19。*
