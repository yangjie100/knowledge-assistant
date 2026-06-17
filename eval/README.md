# knowledge-assistant Eval 基准 (LLM-as-judge)

> Harness Engineering 的 Evals 组件：用 GLM-5.2 当裁判量化 RAG 问答质量，支持 A/B 对比。

## 组成

| 文件 | 作用 |
|------|------|
| golden-qa.jsonl | 10 题 ground truth（基于已灌入的设计文档，确保可检索）|
| rubric.md | 4 维评分标准（准确性/相关性/完整性/接地性，各 0-2，满分 8）|
| judge.py | 主脚本：调 /rag-chain 拿答案 → GLM 按 rubric 打分 → 输出 results-标签.json |
| compare.py | 读两个 results 对比，输出 Markdown 胜率表 |

## 前置

1. ka 服务已启动（默认 localhost:8080），且向量库已灌入 docs/superpowers 文档
2. 设置裁判密钥到环境变量 ZHIPU_API_KEY（智谱 Coding Plan 密钥，与 ka 服务共用同一个）

## 用法

    # 单次评估（默认 reranker 关闭基线）
    python judge.py --label baseline

    # A/B：开 reranker 后再跑（需 KA_RAG_RERANKER_ENABLED 设为 true 重启服务）
    python judge.py --label reranker-on

    # 对比
    python compare.py baseline reranker-on

## 变量

- --api：rag-chain 端点（默认 http://localhost:8080/api/agent/rag-chain）
- --limit N：只跑前 N 题（调试用）
- --judge-model：裁判模型（默认 glm-5.2）
