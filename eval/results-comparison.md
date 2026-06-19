# RAG 基准对照评测：knowledge-assistant vs WeKnora

> 评测日期：2026-06-18  
> 目的：回答"WeKnora 集成进 ka 是否有帮助"——用同语料、同问题、同裁判做公平对照。

## 一、结论（先给结果）

| 系统 | 平均分 | 满分题数 | 平均延迟 | 胜负 |
|---|---|---|---|---|
| **knowledge-assistant**（bge-m3 + reranker + GLM-5.2） | **8.00 / 8** | 10/10 | 11.5s | 胜 |
| **WeKnora**（nomic-embed-text + qwen3.6:35b，无 rerank） | **7.80 / 8** | 8/10 | 23.0s | 负 |

- **质量**：ka 以 80/80 vs 78/80 微胜（2.5%），两分之差均来自 WeKnora 的 **LLM 生成层**（非检索失败）。
- **速度**：ka 快约 **2.0 倍**（11.5s vs 23.0s/题）。
- **集成判断**：**不建议集成 WeKnora**。ka 在同语料上已更准更快；WeKnora 的价值是横向参考（混合检索/Graph），而非替换或嵌入 ka。详见第四节。

## 二、公平性说明（配置差异，非黑盒）

两套系统用同一套语料（`docs/superpowers/` 5 个设计文档）、同一套 10 题（`golden-qa.jsonl`）、同一裁判（GLM-5.2，复用 ka `judge.py` 的 `judge_one`），但技术栈配置不同：

| 维度 | knowledge-assistant | WeKnora | 公平性影响 |
|---|---|---|---|
| Embedding | bge-m3 1024d（中文优化） | nomic-embed-text 768d（英文为主） | ka 占优（中文语料） |
| Rerank | bge-reranker-v2-m3 开 | 关 | ka 占优 |
| LLM | GLM-5.2（云，快） | qwen3.6:35b（本地推理模型，慢） | ka 更快 |
| Chunk | 1000/200 + 中文分隔符 | 1000/200 + 中文分隔符 | 对齐 |
| 裁判 | GLM-5.2 + rubric（4 维 × 0-2） | 同左（复用） | 完全一致 |

> 因此 ka 的胜出部分来自更强的 embedding + rerank + 云 LLM；并非纯架构优势。但反过来看——WeKnora 在"更弱配置 + 无 rerank"下仍拿到 7.8/8，其混合检索（BM25 + 向量）能力相当扎实。

## 三、逐题明细

| 题号 | ka | WeKnora | ka 延迟 | WK 延迟 | 差异 | 丢分点 |
|---|---|---|---|---|---|---|
| q01 向量存储 | 8/8 | 8/8 | 7.8s | 16.6s | 平 | — |
| q02 LLM 组件 | 8/8 | 7/8 | 7.2s | 21.8s | ka +1 | WK 接地性-1：qwen 自行补"Spring AI" |
| q03 ka-rag 职责 | 8/8 | 8/8 | 13.1s | 25.0s | 平 | — |
| q04 切分方法 | 8/8 | 8/8 | 10.9s | 25.9s | 平 | — |
| q05 top5/0.7 | 8/8 | 8/8 | 5.2s | 15.3s | 平 | — |
| q06 对话记忆 | 8/8 | 8/8 | 12.5s | 30.8s | 平 | — |
| q07 文档格式 | 8/8 | 8/8 | 5.1s | 18.9s | 平 | — |
| q08 RAG 流程 | 8/8 | 8/8 | 17.3s | 39.6s | 平 | — |
| q09 诚实回答 | 8/8 | 7/8 | 17.7s | 21.0s | ka +1 | WK 完整性-1：未强调"不要编造" |
| q10 Maven 模块 | 8/8 | 8/8 | 18.1s | 15.4s | 平 | — |

**关键观察**：两题丢分 `refs=10`（召回正常），纯粹是 **LLM 答案润色**问题——
- **q02**：WeKnora 正确答出 Ollama，但 qwen3.6:35b 画蛇添足补了"通过 Spring AI 框架进行调用"（语料无此句）→ 接地性 2→1。
- **q09**：WeKnora 正确答出"诚实回答不知道"，但未点出"不要编造"这一关键约束 → 完整性 2→1。

若 WeKnora 关掉推理模型、换 GLM-5.2 生成，这两分大概率能拿回 → 预期可追平到 8.0/8。这说明 WeKnora 的**短板在 LLM 选型**，不在检索。

## 四、集成建议

**结论：不集成。** 理由：

1. **ka 在同语料上已更准更快**（8.0 vs 7.8，2x 速度），集成 WeKnora 不会提升 ka 的 RAG 质量。
2. **技术栈异构成本高**：WeKnora 是 Go 单体（含 Postgres+Neo4j+Redis 多容器），ka 是 Java 21 + Spring AI 五模块。强行嵌入会引入跨语言、跨运行时的维护负担，违背 ka"轻量可控"的设计目标。
3. **WeKnora 的可借鉴点**（无需集成，作为参考即可）：
   - **混合检索的工程实现**（BM25 + 向量 + RRF 融合）——ka 已自建，可对照 WeKnora 的 Go 实现校验。
   - **Graph RAG / Neo4j 知识图谱**——ka 路线图 P2 的可选项，WeKnora 已内置，可作 PoC 参考。
   - **文档解析 docreader 容器化**——ka 当前解析在 Java 内，WeKnora 的独立 docreader 服务化思路值得借鉴。

**WeKnora 的定位**：横向对标/学习样本，而非 ka 的组件或替代品。

## 五、可复现

- WeKnora 评测脚本：`~/wk_eval/wk_eval.py`（裁判复用 ka `eval/judge.py` 的 `judge_one`，保证打分逻辑一致）
- WeKnora 结果：`~/wk_eval/results-weknora.json`
- ka 基线：`eval/results-bge-m3.json`（8.00/8）
- 工作知识库 ID（WeKnora 内，含 ka 语料 5 文档）：`1eb14280-51d7-4893-96d5-3ab8aefb5895`
