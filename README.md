# knowledge-assistant

**Spring AI 中文一站式知识库问答脚手架** —— 不是 Hello World Demo，而是从 RAG 混合检索、ReAct Agent 到 Docker 部署的完整工程，全部踩坑实测，附 8 篇深度踩坑文章。

## 为什么需要它

Spring AI 官方文档英文且部分包路径有误，市面教程止步于"调一次 Chat 接口"。想把 Spring AI 用到生产级 RAG / Agent，中间隔着几十个文档不会告诉你的坑：

| 痛点 | 常见教程 | 本项目 |
|------|---------|--------|
| 检索只做向量相似度 | `similaritySearch` 一把梭 | BM25(jieba) + 向量 RRF 混合检索 + bge 重排 |
| 中文效果差 | 直接用英文 embedding | bge-m3 1024 维中文 embedding（TEI 推理） |
| Agent 只会单步调用 | 演示一次 ChatClient 调用 | ReAct Agent + Tool Calling 执行循环 |
| 部署无完整方案 | 本地跑 main 方法 | Docker Compose 一键起全套（Redis+Ollama+TEI+App） |
| 中文 LLM 集成无人讲 | 清一色 OpenAI API | GLM 云端主力 + Ollama 全本地降级，双路由 |

## 特性

**RAG 检索链路**
- RRF 混合检索：BM25（jieba 中文分词）+ 向量检索融合，实测评分从 6.10 → 8.00/8
- 中文 embedding：bge-m3（TEI `/embed` 端点，1024 维），bge-reranker-v2-m3 重排
- 检索缓存、分块策略可配置、文档 SHA-256 去重、答案带来源编号

**Agent 与工作流**
- ReAct Agent + 工具调用（`ChatClient.builder().defaultTools()`，执行循环由框架驱动）
- 并行工作流：Spring 受管有界线程池 + 120s 超时降级，不占 ForkJoinPool.commonPool

**对话体验**
- SSE 流式输出，前端 Markdown 渲染 + 代码高亮（marked.js + highlight.js）
- 会话管理侧边栏：列表 / 切换 / 删除，Redis 持久化
- 推理模型 think 块解析（deepseek-r1 格式）

**工程化**
- Maven 多模块（6 模块分层清晰），模块级单元测试全绿
- `mvn verify` 质量闸门：JaCoCo 覆盖率棘轮（不达标即构建失败）
- API Key 认证门禁 + `@Valid` 入参校验；Redis 凭据走 `.env`，仓库零硬编码密钥

## 快速开始（全本地，一条命令）

前置：Docker。

```bash
git clone https://github.com/yangjie100/knowledge-assistant.git
cd knowledge-assistant
cp .env.example .env        # 填 REDIS_PASSWORD
docker compose up -d --build
```

访问 `http://localhost:8080`。首次启动 Ollama 需拉取模型：`docker compose exec ollama ollama pull qwen3.8:27b`。

**用云端 GLM（可选）**：与 profile 无关——本地运行时导出环境变量 `ZHIPU_API_KEY` 即自动装配 GLM 云端主力（`@ConditionalOnExpression` 条件装配）；不设置则全程本地 Ollama。详见 `ka-webapp/src/main/resources/application.yml` 注释。

## 模块架构

```
ka-common        通用模型与工具
   ↓
ka-rag           文档解析/分块/混合检索/重排/缓存
   ↓
ka-chat          对话管理/ChatMemory/SSE 流式
   ↓
ka-agent         ReAct Agent/工作流/MCP
   ↓
ka-webapp        Spring Boot 启动模块 + 前端单页

ka-admin         运维接口（依赖 ka-rag + ka-chat）
```

## 配套踩坑文章（docs/articles/）

每篇文章都来自本仓库的真实开发记录，含错误现场与修复方案：

1. [Spring AI + GLM-5.2 集成的 6 个坑](docs/articles/01-springai-glm52-6-pitfalls.md)
2. [Tool Calling 执行循环：手搭 Advisor 为什么不触发](docs/articles/02-springai-tool-calling-execution-loop.md)
3. [TEI 部署 bge-reranker：模型格式与镜像踩坑](docs/articles/03-springai-tei-reranker.md)
4. [自定义 RAG Advisor 的正确姿势](docs/articles/04-springai-custom-rag-advisor.md)
5. [LLM-as-judge：给 RAG 建一套可复跑的评估基准](docs/articles/05-springai-rag-eval-llm-as-judge.md)
6. [五种工作流范式的工程实现](docs/articles/06-springai-five-workflow-paradigms.md)
7. [MCP Server 从零实践](docs/articles/07-springai-mcp-server-practice.md)
8. [GraphRAG 诚实评估：单跳集上的负优化实录](docs/articles/08-springai-neo4j-graphrag-honest-eval.md)

## 技术栈

| 组件 | 版本/选型 |
|------|----------|
| Java | 21 |
| Spring Boot | 3.3.6 |
| Spring AI | 1.1.4 |
| 向量库 | Redis Stack（FT.SEARCH） |
| Embedding | bge-m3 1024d（TEI 推理） |
| 重排 | bge-reranker-v2-m3（TEI） |
| LLM | GLM（云端）/ qwen3.8:27b、deepseek-r1（Ollama 本地） |
| 构建 | Maven 多模块 + Docker Compose |

## 评估与质量

- 检索质量：LLM-as-judge 10 题黄金集，混合检索 + bge-m3 方案 **8.00/8**（基线 6.10，全程可复跑）
- 单元测试：各模块 `mvn test` 全绿；`mvn verify` 含 JaCoCo 覆盖率闸门

## Roadmap

- [ ] 脚手架通用化：模块改名 + 配置外置文档化
- [ ] 多跳问题黄金集（GraphRAG 价值验证，见文章 8）
- [ ] 前端按 DESIGN.md 规范翻新

## License

[MIT](LICENSE)
