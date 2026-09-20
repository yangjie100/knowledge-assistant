# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Project Instructions for AI Agents

This file provides instructions and context for AI coding agents working on this project.

<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:7510c1e2 -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

**Architecture in one line:** issues live in a local Dolt DB; sync uses `refs/dolt/data` on your git remote; `.beads/issues.jsonl` is a passive export. See https://github.com/gastownhall/beads/blob/main/docs/SYNC_CONCEPTS.md for details and anti-patterns.

## Session Completion

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds
<!-- END BEADS INTEGRATION -->


## 📋 动手前先对齐上下文

> **铁律：先懂业务，再抠代码。** 任何编码/改动任务（新功能、Bug 修复、重构），先读完本文件 + `DESIGN.md` + `docs/`，理解模块职责与已知坑后再进入实现。

- **适用**：新功能开发、Bug 修复、重构、跨模块改动
- **豁免**：改注释、改文档、只读探查、单行 typo
- 建议流程：特性分支 → 测试先行 → 最小实现 → 全量回归 → commit




## 技术栈（⚠️ 覆盖常见的 Java 8 / Spring Boot 2.x 假设，以本节为准）

> 若你的全局配置或直觉 Java 8 / Spring Boot 2.x / 无 Lombok / MyBatis-Plus，**本项目全部不同**：
> - **Java 21**（非 8）→ `var`、`record`、文本块、`switch` 表达式、`List.of()` 均可用
> - **Spring Boot 3.3.6 + Spring AI 1.1.4**（非 2.x）；Jakarta 命名空间（`jakarta.validation`）
> - **有 Lombok**（`@Data`/`@Slf4j`/`@RequiredArgsConstructor` 可用）
> - **无 MyBatis-Plus** → 全局 MyBatis Service 层规范不适用；数据访问用 Spring AI VectorStore + Redis
> - 主存储 **Redis**（向量库 + ChatMemory + 检索缓存），无关系型数据库

## Build & Test

```bash
mvn clean install -DskipTests          # 构建全部 8 模块
mvn test                                # 运行测试（需 Ollama + Redis Stack 在线）
mvn test -pl ka-agent                   # 单模块测试
mvn -pl ka-rag -Dtest=XxxTest test      # 单测试类
mvn spring-boot:run -pl ka-webapp       # 启动（入口在 ka-webapp）
```

### 测试前置依赖（⚠️ 非显而易见，踩过坑）

`mvn test` 的 7 个 `@SpringBootTest` 会加载完整 ApplicationContext，强依赖：

- **Ollama**（`localhost:11434`，模型 `qwen3.8:27b` + `nomic-embed-text`）—— 原生安装，常驻
- **Redis Stack（带 RediSearch 模块）**（`localhost:6379`，密码走 `REDIS_PASSWORD` 环境变量）—— ⚠️ **不是裸 redis**
  - Spring AI `RedisVectorStore` 的 `initialize-schema: true` 会调 `FT._LIST` / `FT.CREATE`，裸 `redis-server` 报 `ERR unknown command FT._LIST` → `vectorStore` bean 创建失败 → 7 个测试全挂
  - `docker-compose.yml` 里的 `redis:7-alpine` **也缺 RediSearch 模块**；生产/集成环境应改用 `redis/redis-stack` 镜像或在 WSL 装 `redis-stack-server`
  - 本机直装方案：`redis-stack-server --daemonize yes --port 6379 --bind 0.0.0.0 --requirepass <REDIS_PASSWORD>`（WSL2 下与 Windows 共享 localhost，application.yml 直连即可）
- **无需** `ZHIPU_API_KEY`：GLM 相关测试在无 key 时走条件化跳过/降级，零回归

### Eval 基准（`eval/`，独立于 Maven 测试套件）

`eval/` 是 **LLM-as-judge 离线评测 harness**（Python），不是单元测试。改 RAG 检索/分块/重排/生成逻辑后用它量化效果：

```bash
cd eval
python judge.py     # 用 GLM-5.2 当裁判跑 golden-qa.jsonl → results-*.json
python compare.py   # A/B 对比多次 results（如 baseline vs reranker-on）
```

- 金标集 `golden-qa.jsonl`、评分细则 `rubric.md`、历史结果 `results-*.json` / `results-comparison.md`
- 跑评测需 `ZHIPU_API_KEY`（裁判模型）+ 运行中的 webapp（judge 调真实 `/api/chat` 端点）
- ⚠️ 改 `eval/` 下评分脚本/金标/rubric 会影响评测口径，commit message 须显式说明动机

## 运维部署

`scripts/ops.sh` 提供带版本标签的部署/秒级回滚，避免新版上线故障无法快速退回：

```bash
./scripts/ops.sh deploy              # 部署（自动打时间戳版本，部署后健康检查）
./scripts/ops.sh deploy v1.2.0       # 部署指定版本号
./scripts/ops.sh rollback            # 秒级回滚到上一个版本（镜像仍保留时）
./scripts/ops.sh rollback 20260616-100000  # 回滚到指定版本
./scripts/ops.sh health              # 健康检查 (actuator/health)
./scripts/ops.sh status              # 服务状态 + 资源占用 + 当前版本
./scripts/ops.sh logs [app|redis|ollama]
./scripts/ops.sh versions            # 历史版本与本地镜像
```

**注意**：每次部署生成独立镜像标签（`ka:时间戳`）并保留，回滚靠切换镜像标签实现，故**清理旧镜像（`docker image prune`）会丧失对应版本的回滚能力**。

**前置依赖：**
- Ollama `http://localhost:11434`（模型 `qwen3.8:27b` + `nomic-embed-text`）
- Redis `localhost:6379`（密码走 `REDIS_PASSWORD` 环境变量，向量索引 `knowledge_assistant`，前缀 `ka_`）
- 可选：环境变量 `ZHIPU_API_KEY`（启用 GLM-5.2 云端模型；无 key 时应用正常启动，零回归）

## Architecture Overview

8 模块 Maven 多模块，parent packaging=pom：

```
ka-common   — DTO(Result)、Model(KnowledgeDocument/ChatMessage)、Exception 基座
ka-rag      — 文档加载/分块/向量化/混合检索(向量+关键词 RRF 融合, topK=5, threshold=0.7) + 可选重排
ka-chat     — ChatClient + SSE 流式 + Redis ChatMemory(MessageWindow, maxMessages=20)
ka-agent    — ReAct Agent + Tool Calling(@Tool) + Chain/Parallel/Routing 工作流
ka-admin    — 知识库 CRUD
ka-webapp   — Spring Boot 启动入口 + 静态前端(index.html/admin.html)
ka-mcp      — 独立可执行 MCP Server，复用 ka-agent @Tool beans (SSE/webmvc 传输)
ka-graph    — GraphRAG (Neo4j)：实体抽取/图谱构建/图谱检索，默认关闭
```

依赖链：`ka-common ← ka-rag ← ka-chat ← {ka-agent, ka-admin} ← ka-webapp`；`ka-mcp ← ka-agent`（独立启动）；`ka-graph`（可选，挂 webapp）

## 可选能力开关（均默认关闭，零回归）

`application.yml` 用环境变量门控三条增强链路，**未启用时对应 bean 全部不创建**（`@ConditionalOnProperty` / `@ConditionalOnExpression`）：

| 能力 | 环境变量 | 作用 | 启用前置 |
|---|---|---|---|
| Embedding 切 TEI bge-m3 1024d | `KA_EMBEDDING_PROVIDER=tei` | 中文向量化质量更好，匹配 GraphRAG 向量索引维度 | 第二个 TEI 容器在 `KA_EMBEDDING_TEI_ENDPOINT` |
| 重排序 (bge-reranker-v2-m3) | `KA_RAG_RERANKER_ENABLED=true` | 召回 topK=20 经交叉编码器精排取 top-N | TEI 容器在 `TEI_ENDPOINT` |
| GraphRAG (Neo4j) | `KA_GRAPH_ENABLED=true` | 实体抽取→图谱构建→多跳检索，补充向量检索 | ka-neo4j 容器 (bolt `KA_GRAPH_NEO4J_URI`) + **bge-m3 1024d embedding**（向量维度须与索引一致）|

> 切换 embedding provider 会改变向量维度 → **必须重建 Redis 向量索引**（删 `ka_*` key 后重新入库），否则检索全 0 召回。

## Conventions & Patterns

- **统一响应**：`Result<T>`（code/success/message/data），定义在 ka-common，所有接口返回它
- **双 ChatClient**（多模型共存，规避 bean 歧义）：
  - `chatClient`（`@Primary`，注入 `OllamaChatModel`）→ ChatService 普通问答
  - `glmChatClient`（`@ConditionalOnExpression` 按 `spring.ai.zhipuai.api-key` 激活，注入 `ZhiPuAiChatModel`）→ Agent 工具调用
- **ReAct Agent**：用 `ChatClient.defaultTools(@Tool beans)` 挂工具（Calculator/DateTime/KnowledgeSearch/KnowledgeStats），**勿手搭 ToolCallAdvisor/DefaultToolCallingManager**
- **可观测性**：已集成 micrometer-tracing + OpenTelemetry + Zipkin

## ⚠️ Spring AI 1.1.4 已知坑（改模型层前必读，均经端到端验证）

1. 多 ChatModel 并存：`spring.ai.model` 是 `@ConditionalOnProperty` 精确匹配，逗号列表（`chat: ollama,zhipuai`）会双双失效；只设 `spring.ai.model.embedding=ollama` 消歧，chat 不设
2. GLM Coding Plan key 必须用 **Coding 端点** `base-url=.../api/coding/paas`（通用 `/api/paas` 从账户余额计费 → HTTP 429 代码 1113 欠费）
3. `@ConditionalOnBean` 在用户 `@Configuration` 上求值时机早于 autoconfig，条件永不匹配 → 改用 `@ConditionalOnExpression`（基于 Environment，与 bean 顺序无关）
4. base-url 勿含尾部 `/v4`（Spring AI 自动追加 `/v4/chat/completions` → `/v4/v4/...` 404）

> 坑点详解与实战过程见 `docs/articles/` 系列文章。
