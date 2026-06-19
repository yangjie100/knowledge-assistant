# Spring AI 1.1.4 + GLM-5.2 集成踩坑实录：6 个坑全修复（2026 最新，附真实代码）

> GLM-5.2（对标 Claude Opus 级别的高阶模型）2026-06-13 才全量开放给智谱 Coding Plan 用户，到今天为止，网上几乎没有 Java / Spring AI 接入它的中文教程。本文记录我在真实项目 `knowledge-assistant`（Spring Boot 3.3.6 + Spring AI 1.1.4 + Java 21，Maven 多模块）里把 GLM-5.2 接进来时踩的 **6 个坑**，每一个都附**真实代码、报错现象、根因分析、修复方案**，不是文档搬运，是掉进去再爬出来的实战记录。

---

## 一、为什么是 GLM-5.2？为什么会有这么多坑？

先说背景，理解了背景你才能理解这些坑为什么"非踩不可"。

- **GLM-5.2** 是智谱 2026-06-13 才开放给 Coding Plan 用户的高阶模型，对标 Claude Opus，能力（尤其是工具调用 / function calling）显著强于普通 GLM-5。
- **Spring AI** 对智谱的支持走的是 `spring-ai-starter-model-zhipuai`，官方第一方 starter，按理说"开箱即用"。
- 但现实是：当你同时想保留 **本地 Ollama（零成本、离线）** 作为兜底，又想让 **GLM-5.2（强工具调用）** 做 Agent 主力时，两套模型配置共存，Spring AI 的自动装配就会在**条件判断、bean 时序、端点路由**上连环踩雷。

这就是这 6 个坑的根源：**不是单模型接入难，是多模型共存难**。

---

## 二、起步：依赖与基础配置

### 1. 引入 Spring AI BOM + GLM starter

`pom.xml`（父 POM）：

```xml
<properties>
    <java.version>21</java.version>
    <spring-ai.version>1.1.4</spring-ai.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<repositories>
    <!-- Spring AI 1.1.x 在 milestone 仓库 -->
    <repository>
        <id>spring-milestones</id>
        <url>https://repo.spring.io/milestone</url>
    </repository>
</repositories>
```

需要 GLM 的模块再加：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-zhipuai</artifactId>
</dependency>
```

> ⚠️ **隐形坑 0**：1.1.x 的 artifact 坐标是 `spring-ai-starter-model-zhipuai`（新版命名），网上大量教程还在写 `spring-ai-zhipuai-spring-boot-starter`（旧版），照抄会找不到依赖。

---

## 三、6 个坑逐一拆解

### 坑 1：两个 EmbeddingModel 导致 `NoUniqueBeanDefinition`

**现象**：同时引入 Ollama 和 ZhiPuAI 的 starter 后，启动直接报：

```
NoUniqueBeanDefinitionException: No qualifying bean of type 'org.springframework.ai.embedding.EmbeddingModel' available: expected single matching bean but found 2: ollamaEmbeddingModel, zhiPuAiEmbeddingModel
```

**根因**：Ollama 和 ZhiPuAI 的自动配置类各自注册了一个 `EmbeddingModel` bean，当某个组件（如 `RedisVectorStore`）按接口注入 `EmbeddingModel` 时，Spring 不知道选哪个。

**修复（二选一，我选的是更彻底的那条）**：

**方案 A（推荐）**：在 `application.yml` 里用 `spring.ai.model.embedding` 关掉不需要的那个。但注意——**它有精确匹配陷阱，见坑 2**。

**方案 B**：自己声明一个 `@Primary` 的 `EmbeddingModel`。我项目里用的是 TEI（bge-m3）做中文 embedding，所以走了这条：

```java
@Configuration
@ConditionalOnProperty(name = "ka.embedding.provider", havingValue = "tei")
public class TeiEmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel teiEmbeddingModel(
            @Value("${ka.embedding.tei.endpoint}") String endpoint,
            @Value("${ka.embedding.tei.timeout-seconds:60}") int timeoutSeconds) {
        return new TeiEmbeddingModel(endpoint, timeoutSeconds);
    }
}
```

关键点：**`@Primary` + 注册顺序先于 Spring AI 的 `@ConditionalOnMissingBean`**。因为用户 `@Configuration` 类先于自动配置处理，TEI 的 bean 先注册，Spring AI 的 `OllamaEmbeddingModel`（带 `@ConditionalOnMissingBean`）发现已有就不再创建，歧义自然消除。

---

### 坑 2：`spring.ai.model` 是**精确匹配**，不是模糊匹配（最隐蔽的坑）

**现象**：为了解决坑 1，我一开始在 yml 里写：

```yaml
spring:
  ai:
    model:
      embedding: ollama,zhipuai   # ❌ 想表达"两个都要"
      chat: ollama,zhipuai        # ❌
```

结果：**Ollama 和 ZhiPuAI 的 chat / embedding 自动配置全部被禁用**，一个 ChatModel 都没装配出来，应用启动后调用直接 NPE。

**根因**：`spring.ai.model.*` 在 1.1.4 是**单值精确匹配**。它内部用 `@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "ollama")` 这种判断，你传一个逗号列表 `"ollama,zhipuai"`，它既不等于 `"ollama"` 也不等于 `"zhipuai"`，于是**两个条件都不成立，两个都被关掉**。这是个"看起来像多选、实际是单选、写错就全灭"的坑。

**修复**：**只约束 EMBEDDING，把 CHAT 留空**。CHAT 不设值时，`matchIfMissing = true` 让 Ollama 和 ZhiPuAI 的 ChatModel **都保持激活**，而歧义交给"按具体类型注入"解决（见坑 3）。

```yaml
spring:
  ai:
    model:
      # 只把 embedding 钉死在 ollama（本地零成本），让 ZhiPuAI 的 embedding autoconfig 关掉
      embedding: ollama
      # chat 故意不写：matchIfMissing 让两个 ChatModel 都活着
```

> 💡 **心智模型**：把 `spring.ai.model.*` 当成"**唯一指定哪个生效**"的开关，不是"白名单"。要共存，就别写，用其他手段消歧。

---

### 坑 3：`ChatModel` 接口注入歧义 → 改注入具体类型

**现象**：坑 2 让两个 `ChatModel`（`OllamaChatModel` + `ZhiPuAiChatModel`）都装配出来了，但当你写 `@Bean ChatClient chatClient(ChatModel chatModel, ...)` 时，又撞回 `NoUniqueBeanDefinition`——这次是 `ChatModel` 接口歧义。

**根因**：和坑 1 同源，只是这次在 Chat 侧。

**修复**：**注入具体实现类型，而不是接口**。让 Spring 按类型精确匹配：

```java
// Ollama 侧（兜底，标 @Primary）
@Configuration
public class OllamaChatClientConfig {

    @Bean
    @Primary
    public ChatClient chatClient(OllamaChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultSystem("你是一个知识库助手……回答使用中文。")
                .build();
    }
}
```

```java
// GLM 侧（Agent / 工具调用主力）
@Configuration
@ConditionalOnExpression("'${spring.ai.zhipuai.api-key:}' != ''")
public class GlmChatClientConfig {

    @Bean
    public ChatClient glmChatClient(ZhiPuAiChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultSystem("你是一个知识库助手……回答使用中文。")
                .build();
    }
}
```

注意方法签名是 `ZhiPuAiChatModel chatModel`、`OllamaChatModel chatModel`——**具体类型**，Spring 直接按类型精确匹配，歧义消失。两个 `ChatClient` bean（`chatClient` 是 `@Primary` 兜底，`glmChatClient` 是 GLM 专用）按名字区分注入。

> 💡 这个"两个 ChatModel 并存 + 两个具名 ChatClient"的写法，是 Spring AI 多模型共存的**标准姿势**，后面坑 4/5/6 都是围绕它展开。

---

### 坑 4：`@ConditionalOnBean` 时序陷阱 → GLM 的 ChatClient 静默不创建

**现象**：GLM 的 ChatClient 写好了，配了 `@ConditionalOnBean(ZhiPuAiChatModel.class)` 想表达"有 GLM 模型才装配"，结果 **`glmChatClient` 静默地从未被创建**，没有任何报错，调用时找不到 bean。

**根因**：这是最阴的一个坑。`@ConditionalOnBean` 在 **bean 定义注册阶段**求值，而你的用户 `@Configuration` 类的处理**早于** Spring AI 的自动配置类——也就是说，当 `@ConditionalOnBean(ZhiPuAiChatModel.class)` 判断时，`ZhiPuAiChatModel` 这个 bean **还没被 autoconfig 注册**，条件永远为 false。

代码注释里我记下了这段血泪：

```java
// NOTE: @ConditionalOnBean(ZhiPuAiChatModel.class) does NOT work here — it evaluates at
// bean-definition registration, BEFORE the ZhiPuAI autoconfig (which runs after user
// @Configuration classes) registers ZhiPuAiChatModel, so the condition never matched and
// glmChatClient was silently never created.
```

**修复**：改用 `@ConditionalOnExpression` 直接读 `Environment` 里的属性——属性在配置处理阶段就可读，**与 bean 装配顺序无关**：

```java
@Configuration
@ConditionalOnExpression("'${spring.ai.zhipuai.api-key:}' != ''")
public class GlmChatClientConfig { ... }
```

> 💡 **心智模型**：在用户 `@Configuration` 上判断"某个 autoconfig 的 bean 是否存在"，**永远不要用 `@ConditionalOnBean`**（顺序坑），改用 `@ConditionalOnProperty` / `@ConditionalOnExpression` 判断驱动它的那个属性。

---

### 坑 5：`base-url` 的 `/v4` 重复，请求 404

**现象**：从智谱文档抄来 base-url `https://open.bigmodel.cn/api/paas/v4`，配上 Spring AI 后调用报 404 / 路径错误。

**根因**：Spring AI 的 ZhiPuAI client 内部会**自己拼接 `/v4/chat/completions`**。如果你 base-url 已经带了 `/v4`，最终请求路径变成 `/api/paas/v4/v4/chat/completions`，404。

**修复**：base-url **省略 `/v4`**，让 Spring AI 自己 append：

```yaml
spring:
  ai:
    zhipuai:
      base-url: https://open.bigmodel.cn/api/paas   # ✅ 不带 /v4
```

> 💡 这个坑很多框架都有（OpenAI 兼容层最爱重复拼版本号），排查时直接看请求的实际 URL。

---

### 坑 6：Coding 端点 vs 普通端点 —— HTTP 429 / code 1113 "账户欠费"

**现象**：这是最坑钱的一个。配好之后调用 GLM，报 `HTTP 429`，错误码 `1113`（账户欠费 / arrears）。但我明明买了 Coding Plan，额度充足。

**根因**：智谱有**两套端点**：

| 端点 | 路径 | 计费 |
|---|---|---|
| 普通端点 | `https://open.bigmodel.cn/api/paas` | **扣账户余额** |
| **Coding 端点** | `https://open.bigmodel.cn/api/coding/paas` | **走 Coding Plan 免费额度** |

你的 Coding Plan API key **只在 Coding 端点**才扣套餐免费额度。如果你配的是普通端点 `/api/paas`，它会去扣**账户余额**——而你的账户余额很可能是空的，于是报 `1113 欠费`，看起来像是"额度有问题"，其实是**端点配错了**。

**修复**：base-url 必须用 Coding 端点：

```yaml
spring:
  ai:
    zhipuai:
      # ✅ 用 CODING 端点 /api/coding/paas，不是 /api/paas
      # Coding Plan key 只在 Coding 端点抽免费额度；普通端点扣账户余额（空余额 → 429/1113 欠费）
      # Spring AI 自己会 append /v4/chat/completions，所以省略 /v4
      base-url: ${ZHIPU_BASE_URL:https://open.bigmodel.cn/api/coding/paas}
      api-key: ${ZHIPU_API_KEY:}
      chat:
        options:
          model: ${ZHIPU_MODEL:glm-5.2}
```

> 💡 **这个坑官方文档藏在角落里**。如果你接到 `1113` 而不是常见的限流码（`1302` 速率限制 / `1310` 周额度上限），第一反应就该是：**查 base-url 是不是 Coding 端点**。

---

## 四、最终配置清单（可直接抄）

### `application.yml`

```yaml
spring:
  ai:
    model:
      embedding: ollama                 # 坑2：只钉 embedding，chat 留空让两 ChatModel 共存
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: deepseek-r1:32b
      embedding:
        options:
          model: nomic-embed-text
    zhipuai:
      base-url: ${ZHIPU_BASE_URL:https://open.bigmodel.cn/api/coding/paas}   # 坑5+坑6
      api-key: ${ZHIPU_API_KEY:}
      chat:
        options:
          model: ${ZHIPU_MODEL:glm-5.2}
          temperature: 0.7
```

### 两个 ChatClient 配置类

上面坑 3 已给出完整代码，核心就两条：

1. `OllamaChatClientConfig`：注入 `OllamaChatModel`（具体类型），`@Primary`。
2. `GlmChatClientConfig`：注入 `ZhiPuAiChatModel`（具体类型），`@ConditionalOnExpression` 门控 api-key（坑 4）。

---

## 五、验证

配好后，用一个最简单的 Agent 工具调用测（GLM-5.2 的强项）：

```java
ChatClient.builder(chatModel)
    .defaultTools(dateTimeTool, calculatorTool)   // @Tool 注解的 bean
    .build()
    .prompt("今天是几号？")
    .call()
    .content();
```

GLM-5.2 会正确触发工具执行循环，返回真实当前日期。如果它"把工具调用当文本输出来"，那是另一个坑（手搭 `ToolCallAdvisor` 不触发执行循环），见本系列下一篇。

---

## 六、避坑速查表

| # | 坑 | 一句话 | 修复关键词 |
|---|---|---|---|
| 1 | EmbeddingModel 歧义 | 两个 starter 各注册一个 | `@Primary` / `spring.ai.model.embedding` |
| 2 | model 精确匹配 | 逗号列表 = 全灭 | 只设 embedding，chat 留空 |
| 3 | ChatModel 歧义 | 接口注入选不出 | 注入具体类型 |
| 4 | `@ConditionalOnBean` 时序 | 判断时 bean 还没注册 | 改 `@ConditionalOnExpression` |
| 5 | `/v4` 重复 | 框架自己拼 `/v4` | base-url 省 `/v4` |
| 6 | Coding 端点 | 普通 endpoint 扣余额→1113 | `/api/coding/paas` |

---

## 写在最后

这 6 个坑里，**坑 2（精确匹配）和坑 6（Coding 端点）杀伤力最大**——一个让你的模型全消失还不知道为什么，一个让你以为"额度有问题"其实在扣空余额。Spring AI 官方文档对中文场景、对 Coding Plan 这套计费端点几乎没提，只能靠踩。

下一篇我会写 **Spring AI 工具调用（function calling）从"完全失效"到"稳定触发执行循环"的根因**——`GLM tool_calls 被当文本输出` 这个坑，比这 6 个加起来还费时间。

> 本文所有代码来自真实项目 `knowledge-assistant`（Spring AI 中文一站式脚手架）。这是我做"Java + Spring AI 中文赛道"内容的一部分，目标是把官方文档里没讲、英文社区零散、中文社区空白的部分，系统补齐。
