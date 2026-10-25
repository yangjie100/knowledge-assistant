# 8D 智能报告生成 Demo

质量异常记录 → 8D 方法论分析报告初稿（Markdown/Word）。复用 knowledge-assistant 底座：
RAG 检索历史案例 + 大模型单次生成，**支持纯本地离线运行**。

## 架构

```
MES 异常记录(JSON)
   │  POST /api/agent/8d/generate
   ▼
EightDController ──► EightDReportService
                        ├─ 1. RetrievalService 混合检索(本地 Redis 向量库, nomic-embed-text)
                        │      └─ 命中历史 8D 案例 chunk 作为参照
                        └─ 2. EightDChatClient 单次生成(无 Agent 循环, 结构稳定)
                               ├─ 开发态: GLM(ZHIPU_API_KEY 存在时)
                               └─ 演示态: Ollama qwen3.8:27b(key 摘除时)
   ▼
markdown 报告 ──► md-to-word-report(merge_to_docx.py) ──► Word 初稿(.docx)
```

双态切换原理：`EightDConfig` 两个同名 `@Bean("eightDChatClient")` 按
`spring.ai.zhipuai.api-key` 互斥条件激活（与 `AgentExecutorConfig` 同款模式）。

## 数据（全部手造脱敏）

| 目录 | 内容 |
|---|---|
| `history/` | 3 份历史 8D 结案案例（波峰焊虚焊/钽电容反装/连接器接触不良） |
| `records/` | 5 条质量异常记录（3 条命中历史案例类型，2 条新类型验证泛化） |
| `output/` | 双态生成样例报告 + Word 初稿 |

## 运行

### 前置

- JDK 21、Redis Stack（`redis-stack-server --daemonize yes --requirepass $REDIS_PASSWORD`）
- Ollama：`qwen3.8:27b` + `nomic-embed-text`
- 案例入库一次（embedding 用本地 ollama，与双态无关，索引共用）：

```bash
for f in docs/demo-8d/history/8d-case-*.md; do
  curl -s -F "file=@$f" http://localhost:8080/api/knowledge/upload; done
```

### 启动应用

```bash
# 开发态（GLM，快）：ZHIPU_API_KEY 从环境继承
java -jar ka-webapp/target/ka-webapp-1.0.0.jar

# 演示态（纯本地，拔网线可跑）：摘 key + 关 ZhiPu autoconfig
env -u ZHIPU_API_KEY SPRING_AI_MODEL_CHAT=ollama SPRING_AI_MODEL_IMAGE=none \
  java -jar ka-webapp/target/ka-webapp-1.0.0.jar
```

> 坑：摘 key 后必须 `SPRING_AI_MODEL_CHAT=ollama SPRING_AI_MODEL_IMAGE=none`，
> 否则 ZhiPuAi chat/image autoconfig 仍会因缺 key 启动失败（ka CLAUDE.md 已知问题）。

### 生成 8D 报告

```bash
curl -X POST http://localhost:8080/api/agent/8d/generate \
  -H "Content-Type: application/json" \
  --data-binary @docs/demo-8d/records/QR-2026-1001.json
# 响应: {"success":true,"data":{"format":"markdown","report":"# 8D ..."}}
# 耗时参考: GLM ≈170s / 本地 qwen3.8:27b ≈170s，报告 5000-9000 字符
```

### 转 Word

```bash
# 依赖(一次): py311 -m pip install python-docx latex2mathml
"D:/Users/admin/anaconda3/envs/py311/python.exe" build_8d_docx.py   # 见 ~/.claude/skills/md-to-word-report
```

## 验证记录（2026-10-25）

| 验证点 | GLM 态 | 本地降级态 |
|---|---|---|
| `mvn test -pl ka-agent` | 43 tests 全绿（新增 12） | 同一构建 |
| 端到端 200 + 报告生成 | ✅ 169s / 5006 字符 | ✅ 170s / 8673 字符 |
| D1–D8 结构完整性 | ✅ | ✅ |
| 5Why + 直接/流出/系统原因 | ✅ | ✅ |
| 记录数据原样引用 | ✅ (QR-2026-1001/ZK-A5/150) | ✅ |
| RAG 命中 | ✅ similar cases=5 | ✅ similar cases=5（索引共用，零重入库） |
| Word 转换 | — | ✅ 14 表格，D1-D8 全标题，封面正确 |
