# KA MCP Server

将 knowledge-assistant 的 4 个 `@Tool` 暴露为标准 **MCP(Model Context Protocol)Server**,供 Claude Code、Cursor 等 MCP 客户端直接调用。

## 暴露的工具

| 工具 | 说明 | 参数 |
|------|------|------|
| `getCurrentDateTime` | 当前日期时间(含星期) | 无 |
| `calculate` | 数学表达式求值(支持 `+ - * /` 和括号) | `expression` |
| `getStats` | 知识库系统状态 | 无 |
| `searchKnowledge` | 语义检索知识库 | `query`(自然语言) |

## 架构

独立 Spring Boot 应用(webmvc + SSE transport),复用 `ka-agent` 的 `@Tool` bean,通过 `MethodToolCallbackProvider` 装配为 MCP 工具。**不依赖 ka-webapp**,关注点分离、可独立部署。

```
MCP Client (Claude Code / Cursor)
    ↓  SSE: GET /sse  +  POST /mcp/message?sessionId=...
ka-mcp  (port 8090)
    ↓  @Tool 方法调用
ka-agent tools  →  ka-rag (RetrievalService)
    ↓  query embedding + 向量检索
TEI bge-m3 (8084)  +  Redis (knowledge_assistant 索引, 1024d)
```

## 前置依赖

`searchKnowledge` 走完整 RAG 链,需要:

1. **Redis** 运行(6379),且 `knowledge_assistant` 向量索引已建并有文档
2. **TEI bge-m3** 运行(8084)—— embedding 模型,**维度必须与索引一致(1024d)**

```bash
# TEI 停了就启动(首次加载模型约 99s)
docker start tei-embedder
curl http://localhost:8084/health   # 200 = 就绪
```

> ⚠️ **关键坑**:Redis 索引维度必须与 embedding 模型一致。本项目索引用 bge-m3(1024d)建,所以 ka-mcp 必须用 TEI(bge-m3),**不能用 ollama nomic(768d)**,否则报:
> `vector blob size (3072) does not match index's expected size (4096)`(3072B=768d×4, 4096B=1024d×4)。

## 启动

```bash
# 1. 必须用 JDK 21(项目要求;PATH 里的 java 若是 Java 8 会启动失败)
# 2. 必须对齐索引维度:
export KA_EMBEDDING_PROVIDER=tei
export KA_EMBEDDING_TEI_ENDPOINT=http://localhost:8084
export REDIS_PASSWORD=...          # 你的 Redis 密码
export ZHIPU_API_KEY=...           # 仅当工具需调 GLM 时(可选)

"D:/Program Files/Java/jdk-21/bin/java.exe" -jar ka-mcp/target/ka-mcp-1.0.0.jar
```

开发期也可:`mvn -pl ka-mcp spring-boot:run`(**不要带 `-am`**,否则 reactor 在非应用模块报 `mainClass` 错)。

启动成功标志:`GET http://localhost:8090/sse` 返回 `event: endpoint` + `data: /mcp/message?sessionId=...`。

## 接入 Claude Code

**方式 A:项目级 `.mcp.json`**(放在仓库根,团队共享)
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

**方式 B:命令行注册**
```bash
claude mcp add ka-mcp --transport sse http://localhost:8090/sse
```

重启 Claude Code,`/mcp` 可见 `ka-mcp` 已连接,4 个工具自动可用(模型按需调用)。

## 接入 Cursor

`~/.cursor/mcp.json`:
```json
{
  "mcpServers": {
    "ka-mcp": { "url": "http://localhost:8090/sse" }
  }
}
```

## 安全提醒

- **8090 端口默认无鉴权**(Spring AI 1.x webmvc MCP starter 未内置鉴权机制)。本地开发可接受。
- 生产 / 内网部署前**必须**加固(任选其一):
  - 绑定 `server.address: 127.0.0.1`(仅本机),或
  - 前置反向代理加 Basic Auth / Bearer Token / mTLS
- 未授权访问可调用全部工具,包括 `searchKnowledge` 读取知识库全文 + 间接消耗 GLM API 额度。

## 故障排查

| 现象 | 原因 | 解决 |
|------|------|------|
| client 连不上 | ka-mcp 未启动 / 端口错 | 确认 8090 监听,`GET /sse` 返回 endpoint 帧 |
| `searchKnowledge` 报维度不匹配 | embedding 维度 ≠ 索引维度 | `KA_EMBEDDING_PROVIDER=tei` + TEI 8084 在跑 |
| `searchKnowledge` 返回空 | TEI 停了 / Redis 无数据 | `docker start tei-embedder`;确认索引有文档 |
| 启动报 `mainClass` 找不到 | 用了 `spring-boot:run -am` | 改 `package -DskipTests` + `java -jar`,或不带 `-am` |
| 启动报 Java 版本错 | PATH 的 java 是 Java 8 | 用 JDK 21 绝对路径 |
| 自测 `searchKnowledge` 中文检索空(curl) | `curl -d` 中文走 GBK 污染 | 用 `--data-binary @utf8file`(Claude Code/Cursor 客户端默认 UTF-8,无此问题) |
