# Ollama 不支持 Reranker：用 HuggingFace TEI 给 Spring AI RAG 加重排（含中文 BM25 召回为 0 的根因）

> [Spring AI 中文踩坑系列] 第三篇。如果你的 RAG 检索结果"召回了一堆，但最相关的那条排在第五名开外"，你需要的是 **reranker（重排器）**。但很多人第一反应是"用 Ollama 跑一个"——这条路**走不通**，原因不是配置问题，是**模型架构问题**。本文讲清为什么、怎么办，并附完整的 TEI + RRF 混合检索 + 多层降级容错的工程实现。

---

## 一、为什么 RAG 需要 Reranker？

向量检索（embedding similarity）的召回是**粗排**：它用双塔结构（bi-encoder）把 query 和文档各自编码成向量算余弦，速度快、能对海量文档建索引，但**精度有限**——它没有让 query 和文档"逐字交互"。

Reranker 是**精排**：用 cross-encoder 把 `(query, document)` 拼在一起送进模型，让两者在每个 token 层面交叉注意力，输出一个**精确的相关性分数**。精度远高于向量相似度，但计算贵（每对 query-doc 都要过一次模型），所以只能对**少量候选**（top 20）做。

经典 RAG 检索链路：

```
query → 向量检索(粗排 top20) + 关键词检索(BM25 top20) → RRF融合 → Reranker(精排) → top5 → LLM
```

问题是：**Spring AI 1.1.4 没有内置 reranker**（官方 issue #5903 还开着）。你得自己接。

---

## 二、为什么 Ollama 不能做 Reranker？（架构层面）

这是最容易被误解的一点。很多人想："我已经用 Ollama 跑 embedding 了，再跑个 reranker 不就行了？"——**不行**。

**根因：reranker 和 embedding/聊天模型是两类完全不同的模型架构。**

| 模型类型 | 架构 | 输入 | 输出 | Ollama 支持？ |
|---|---|---|---|---|
| Chat（聊天） | decoder-only LLM | token 序列 | **token 序列**（文本） | ✅ |
| Embedding | bi-encoder | 单段文本 | **向量**（float 数组） | ✅ |
| **Reranker** | **cross-encoder** | **(query, doc) 对** | **一个标量分数**（scalar） | ❌ |

Ollama 的整个 API 设计是围绕**"输入 token → 输出 token"**的生成范式（哪怕是 embedding 也是借了模型的能力）。而 reranker 的输出是一个**相关性分数**，不是 token 序列——它压根不"生成"任何东西，只跑一次前向传播出一个 logit。

**所以 Ollama 的 API 根本没有表达"给我一对文本，我返回一个分数"的接口。** 你哪怕把 bge-reranker 的权重塞进去，Ollama 也不知道怎么把它当 reranker 用。这不是 Ollama 的 bug，是它不在 reranker 这个能力域里。

我在项目里实测验证过这个结论：试图用 Ollama + DocumentPostProcessor 做 reranker，模型返回的是一堆 token 文本，根本不是分数。

---

## 三、正解：HuggingFace TEI + bge-reranker-v2-m3

TEI（Text Embeddings Inference）是 HuggingFace 出的**专用推理服务**，同时支持 embedding 模型和 reranker 模型（cross-encoder），提供标准的 `/embed` 和 `/rerank` HTTP 接口。

我用的是 `bge-reranker-v2-m3`（中文友好）。

### 部署 TEI 的坑（一人公司本地部署向）

部署 TEI 时踩了一串坑，列出来省你时间：

1. **`hf.co` 被墙**：TEI 启动会去 huggingface.co 拉模型，国内拉不动。改用镜像 `hf-mirror.com`（设环境变量 `HF_ENDPOINT=https://hf-mirror.com`）。
2. **`hf-mirror` 不支持 `Content-Range`**：TEI 的 Rust 下载器（hf-hub crate）要求服务器返回 `Content-Range` 头支持分段下载，hf-mirror 不返回 → 下载失败。**解法：在宿主机用浏览器/curl 把模型文件下到本地，挂载进容器**，TEI 用本地路径加载。
3. **一容器一模型**：TEI 一个容器只能跑一个模型。要同时跑 embedding（bge-m3）和 reranker（bge-reranker-v2-m3），**得起两个容器**，端口分开。
4. **Git Bash 路径转换**：在 Windows Git Bash 里给 Docker 传 `--model-id /path/to/model`，会被 MSYS 路径转换搞坏。加 `MSYS_NO_PATHCONV=1` 前缀。
5. **CPU 性能**：CPU 跑 reranker，8 个候选 + 超时 120s 大约 **39s** 才返回；如果候选数拉到 20（16 个候选），CPU 可能超 30s。**生产强烈建议上 GPU**（3090Ti 级别约 20–50ms）。本地开发可以接受慢，生产不行。

> 💡 如果你只做 PoC，CPU 凑合；如果要上生产，reranker 必须 GPU。

---

## 四、代码实现

### 1. 配置：`inert-until-configured`（不配不启用）设计哲学

```java
@Data
@Component
@ConfigurationProperties(prefix = "ka.rag.reranker")
public class RerankerConfig {
    // TEI 是额外基础设施，不能假设它永远部署着。默认关闭，避免没部署 TEI 时每个请求都降级 + 刷 WARN 日志
    private boolean enabled = false;
    private String endpoint = "http://localhost:8082";   // 改成你的 TEI reranker 端口
    private int recallTopK = 20;       // 开启 rerank 时，召回这么多候选给 reranker 重排
    private double recallThreshold = 0.5;  // 0.7 在中文语料上凑不满 20 条，所以降到 0.5
    private int topN = 5;              // rerank 后保留前 N
    private int timeoutSeconds = 10;
}
```

**关键设计：`enabled = false` 默认关闭。** TEI 是"额外基础设施"，开发环境不一定装。如果默认开启，没装 TEI 时每个请求都会连接失败降级，刷一堆 WARN。`enabled` 标志让 reranker **惰性启用**——本地开发不开，compose/生产环境用环境变量 `KA_RAG_RERANKER_ENABLED=true` 开。

```yaml
ka:
  rag:
    reranker:
      enabled: ${KA_RAG_RERANKER_ENABLED:false}
      endpoint: ${TEI_ENDPOINT:http://localhost:8082}
      recall-top-k: 20
      top-n: 5
```

### 2. RerankClient：调 TEI `/rerank`，失败返回 null 作为降级信号

```java
@Component
public class RerankClient {

    private final RestTemplate restTemplate;
    private final String endpoint;

    @Autowired   // 消歧：不加的话 Spring 看到两个构造器（下面还有测试用的）会报"No default constructor"
    public RerankClient(RerankerConfig config) {
        this(buildRestTemplate(config), config.getEndpoint());
    }

    // 测试专用：直接注入 RestTemplate（Mockito mock 或 MockRestServiceServer）
    RerankClient(RestTemplate restTemplate, String endpoint) {
        this.restTemplate = restTemplate;
        this.endpoint = endpoint;
    }

    /**
     * @return 排序后的结果；连接/超时/响应不完整返回 null，通知 RerankService 降级
     */
    public List<RerankResult> rerank(String query, List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        try {
            ResponseEntity<RerankResult[]> resp = restTemplate.postForEntity(
                    endpoint + "/rerank",
                    new RerankRequest(query, texts, true),   // record，RestTemplate 自带 Jackson 自动序列化
                    RerankResult[].class);
            RerankResult[] body = resp.getBody();
            if (body == null) return null;
            // 长度校验：截断/乱码的响应会导致 index 悬空
            if (body.length != texts.size()) return null;
            return Arrays.asList(body);
        } catch (ResourceAccessException e) {   // 连接/超时
            return null;
        } catch (RestClientException e) {
            return null;
        }
    }
}
```

两个设计要点：

1. **失败返回 `null`，不抛异常**。`null` 是给上层 `RerankService` 的"我搞不定，你降级吧"信号。抛异常会把降级决策耦合进 client，不如用返回值表达。
2. **响应长度校验**。TEI 返回的是 `{index, score}` 数组，`index` 指回原 texts 列表。如果响应被截断（`body.length != texts.size()`），后续按 index 取文档会越界，所以长度不符直接降级。

> 💡 `@Autowired` 那个注释是真踩过的坑：为了写单测加了个注入 RestTemplate 的第二构造器，结果 Spring 看到两个构造器不知道选哪个，应用启动直接失败。给生产构造器加 `@Autowired` 明确指定，问题消失。

### 3. RerankService：多层降级容错

```java
@Service
@RequiredArgsConstructor
public class RerankService {

    private final RerankClient rerankClient;
    private final RerankerConfig config;

    public List<Document> rerank(List<Document> candidates, String query) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        List<String> texts = candidates.stream().map(Document::getText).toList();
        List<RerankResult> results;
        try {
            results = rerankClient.rerank(query, texts);
        } catch (Exception e) {
            // 顶层兜底：哪怕是个意料外的异常，也不能让整个请求挂掉
            log.warn("Rerank threw unexpected error, degrading: {}", e.getMessage());
            results = null;
        }

        // 降级路径：回退到 RRF 分数排序取 topN
        if (results == null) {
            return candidates.stream()
                    .sorted(Comparator.comparingDouble(this::rrfScoreOf).reversed())
                    .limit(config.getTopN())
                    .toList();
        }

        // 正常路径：按 rerank 分数排序，带双重防护
        return results.stream()
                .sorted(Comparator.comparingDouble(RerankResult::score).reversed())  // 防御性二次排序
                .filter(r -> r.index() >= 0 && r.index() < candidates.size())        // 越界防护
                .limit(config.getTopN())
                .map(r -> {
                    Document d = candidates.get(r.index());
                    d.getMetadata().put("rerankScore", r.score());   // 分数写回 metadata，供缓存用
                    return d;
                })
                .toList();
    }
}
```

**降级哲学**：reranker 是"锦上添花"的精排，它的故障**绝不能拖垮主问答流程**。所以设计了三层兜底：

1. RerankClient 内部：连接/超时 → 返回 null；
2. RerankService 的 try-catch：意料外异常 → 也置 null；
3. null 时：回退到 RRF 分数（粗排分数）排序取 topN。

没有 TEI、TEI 挂了、TEI 返回乱码、超时——任何一种情况，问答都不中断，只是退回到粗排质量。**这是个该有的健壮性设计**。

### 4. HybridRetrievalService：完整检索链路编排

```java
public List<Document> hybridRetrieve(String query) {
    // 1. 缓存命中直接返回
    List<CachedResult> cached = getFromCache(query);
    if (cached != null) return reconstructDocuments(cached);

    // 2. 根据是否开启 rerank，决定召回数量
    boolean rerank = rerankerConfig.isEnabled();
    int recallK = rerank ? rerankerConfig.getRecallTopK() : TOP_K;          // 开 rerank 召回 20，否则 5
    double threshold = rerank ? rerankerConfig.getRecallThreshold() : SIMILARITY_THRESHOLD;

    // 3. 双路召回
    List<Document> vectorResults = vectorStore.similaritySearch(
        SearchRequest.builder().query(query).topK(recallK).similarityThreshold(threshold).build());
    List<KeywordResult> keywordResults = keywordSearch(query, recallK);

    // 4. RRF 融合
    List<Document> fused = rrfFusion(vectorResults, keywordResults,
        rerank ? rerankerConfig.getRecallTopK() : TOP_K);

    // 5. rerank 精排（或直接截断 top5）
    List<Document> result = rerank
        ? rerankService.rerank(fused, query)
        : fused.stream().limit(TOP_K).toList();

    // 6. 写缓存（5 分钟 TTL）
    saveToCache(query, result);
    return result;
}
```

RRF（Reciprocal Rank Fusion）融合算法核心就一行：

```java
// RRF_K = 60
rrfScores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
```

每个文档在每路检索里的得分是 `1/(60 + rank)`，两路相加。这样**不依赖各路分数的绝对值和量纲**（向量余弦 ~0.7，BM25 可能几十几百，量纲不同无法直接加权），只看排名，公平融合。

---

## 五、附赠大坑：中文 BM25 召回为 0 的根因（jieba 分词）

写 reranker 时顺带发现了一个更要命的坑：**关键词检索（BM25）对中文查询的召回居然是 0**。

**根因**在 `keywordSearch` 的注释里写得很清楚：

```java
// Chinese sentences carry no whitespace, so RediSearch's default tokenizer turns the
// whole query into one unusable token (root cause of q02/q06 zero BM25 recall). Segment
// the query into keywords and build an OR expression so any single keyword surfacing a
// document is enough.
String segmented = chineseTokenizer.toRediSearchQuery(query);
```

RediSearch（Redis 的全文搜索）默认 tokenizer **按空格切词**。中文句子没有空格，于是整句 `"系统的架构是怎样的"` 被当成**一个不可拆分的巨型 token**——这个 token 在索引里当然不存在，BM25 召回直接为 0。

**修复**：先用中文分词（jieba）把 query 切成词，拼成 RediSearch 的 OR 查询表达式，让任意一个词命中就能召回该文档：

```java
List<KeywordResult> keywordSearch(String query, int limit) {
    String segmented = chineseTokenizer.toRediSearchQuery(query);
    String searchQuery = segmented != null ? segmented : query;   // 分词失败回退原 query
    // 用 Jedis sendCommand 直接发 FT.SEARCH（绕过 Spring Data Redis 抽象，它不暴露 FT 命令）
    Object result = redisTemplate.execute((RedisCallback<Object>) connection -> {
        Jedis jedis = (Jedis) connection.getNativeConnection();
        return jedis.sendCommand(
            () -> SafeEncoder.encode("FT.SEARCH"),
            SafeEncoder.encode("chunk-idx"),
            SafeEncoder.encode(searchQuery),
            SafeEncoder.encode("LIMIT"), SafeEncoder.encode("0"), SafeEncoder.encode(String.valueOf(limit)));
    });
    return parseFtSearchResult(result);
}
```

> 💡 这里还有个知识点：**Spring Data Redis 的抽象不暴露 RediSearch 的 `FT.SEARCH` 命令**。要用 RediSearch 全文搜索，得拿到底层 Jedis 连接，用 `sendCommand` 直接发原生命令。这也是个隐形坑。

修复中文分词后，我的 RAG eval 黄金集里 q02/q06 的召回从 0 恢复，整体分数从 6.10 跳到 7.80（详见本系列后续 eval 篇）。

---

## 六、效果与性能

开启 reranker 后（`KA_RAG_RERANKER_ENABLED=true`），端到端验证：

- 日志出现 `Rerank applied: 8 candidates -> 5 results`（**真实调用，非降级**）；
- rerank 分数是 logit 量级（如 1.70 / -0.78 / -1.11），明显区别于 RRF 的 ~0.016；
- **排名实变**：基准第 3 名的文档被淘汰，第 4 名升到第 3——证明 reranker 真的改变了排序，不只是走过场。

性能（CPU）：

| 候选数 | 耗时 | 说明 |
|---|---|---|
| 8 | ~39s | 可接受（PoC） |
| 16（recallTopK=20）| 超 30s，触发降级 | CPU 撑不住，回退 RRF |

**结论**：本地 CPU 开发 recallTopK 设 8–10；生产必须 GPU。

---

## 七、避坑速查表

| 坑 | 现象 | 根因 | 修复 |
|---|---|---|---|
| Ollama 跑 reranker | 返回 token 文本不是分数 | 生成式 vs cross-encoder 架构差异 | 用 TEI |
| Spring AI 无内置 reranker | 找不到 API | 官方未实现（issue #5903） | 自建 RerankService 接 TEI |
| hf.co 拉模型失败 | 超时 | 被墙 | hf-mirror + 本地挂载 |
| 一容器跑多模型 | 报错 | TEI 一容器一模型 | 起 N 个容器 |
| 中文 BM25 召回 0 | 关键词检索空结果 | RediSearch 默认按空格切词，中文整句成单 token | jieba 分词 + OR 查询 |
| 想用 FT.SEARCH 找不到 API | Spring Data Redis 无此方法 | 抽象层不暴露 | Jedis sendCommand 发原生命令 |
| CPU reranker 慢 | 超时降级 | cross-encoder 计算量大 | 上 GPU |
| reranker 故障拖垮问答 | 主流程挂 | 没做降级 | null 信号 + 多层兜底回退 RRF |

---

## 写在最后

这一篇信息密度很高，因为它串起了 RAG 检索质量的三件大事：**reranker（精排）、RRF（融合）、中文分词（BM25 召回）**。很多人做 RAG 卡在"明明检索到了，但答案不好"，根因往往就是缺 reranker + 中文分词没做。

一句话总结：**Ollama 是生成式，做不了 reranker；用 TEI 跑 cross-encoder；务必做多层降级；顺便把中文分词修了，BM25 才不会召回为 0。**

下一篇讲 **Spring AI 1.1.4 没有 `RetrievalAugmentationAdvisor`，如何自建 `BaseAdvisor` 把上面这套 RAG 编织进 Advisor 链**。

> 本文代码来自真实项目 `knowledge-assistant`，全套 RAG 检索（向量+BM25+RRF+Rerank+缓存）已在该项目落地。
