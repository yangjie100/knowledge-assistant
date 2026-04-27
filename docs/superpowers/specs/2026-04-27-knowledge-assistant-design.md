# Knowledge Assistant 设计文档

> 通用可定制的智能客服/问答系统，基于 Spring AI + Ollama + Redis VectorStore

## 技术栈

| 组件 | 版本/说明 |
|------|-----------|
| Spring Boot | 3.3.6 |
| Spring AI | 1.0.0 |
| Java | 21 |
| LLM | Ollama（本地 qwen2.5/deepseek） |
| 向量存储 | Redis VectorStore |
| 构建 | Maven 多模块 |
| 前端 | 静态 HTML + vanilla JS + Tailwind CDN |

## 模块划分

```
knowledge-assistant/
├── pom.xml                    # 父 POM（spring-ai-bom）
├── ka-common/                 # 公共模型、工具类、异常
├── ka-rag/                    # RAG 引擎（文档加载/切分/嵌入/检索）
├── ka-chat/                   # 对话管理（ChatClient/记忆/流式输出）
├── ka-admin/                  # 管理 API（知识库/对话 CRUD）
└── ka-webapp/                 # 启动模块 + 前端页面
```

### 依赖关系

```
ka-webapp → ka-admin → ka-chat → ka-rag → ka-common
                                     → ka-common
```

## 模块详细设计

### ka-common

| 包 | 内容 |
|---|------|
| model | KnowledgeDocument（id, title, sourceType, chunkCount, createTime）、ChatMessage（role, content, timestamp） |
| exception | AiServiceException、DocumentParseException |
| config | Redis 配置、Ollama 连接配置 |
| dto | Result<T> 统一响应（复用 hermesTest 模式） |

### ka-rag（RAG 引擎）

**文档加载器：** DocumentLoader 接口 + 4 种实现

| 实现类 | 支持格式 |
|--------|---------|
| PdfDocumentLoader | .pdf |
| TextDocumentLoader | .txt |
| MarkdownDocumentLoader | .md |
| HtmlDocumentLoader | .html |

**切分：** TokenTextSplitter，按 token 数切分，overlap 保留上下文连续性

**嵌入服务 EmbeddingService：**
- 接收文档 -> 切分为 chunk -> 调用 Ollama Embedding -> 存入 Redis VectorStore
- 每个文档记录元数据（source, title, chunkIndex）

**检索服务 RetrievalService：**
- 接收 query -> 向量化 -> Redis 相似度检索 -> 返回 top-K 文档片段
- 默认 top-K = 5，相似度阈值 0.7

**关键流程：**
```
上传文档 -> 解析 -> 切分 -> 嵌入 -> 存入 Redis VectorStore
```

### ka-chat（对话管理）

**ChatClient 配置：** OllamaChatClientConfig 创建 ChatClient Bean
**System Prompt：** 你是一个知识库助手。根据提供的上下文文档回答用户问题。如果上下文中没有相关信息，请诚实回答不知道。

**对话记忆：** InMemoryChatMemory，按 conversationId 隔离，通过 MessageChatMemoryAdvisor 集成

**ChatService 流程：**
1. 接收用户问题 + conversationId
2. 调用 RetrievalService 获取相关文档片段
3. 组装 Prompt（system prompt + context + 历史 + 问题）
4. 调用 ChatClient 生成回答（流式 SSE）

### ka-admin（管理 API）

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | /api/knowledge/upload | 上传文档（MultipartFile） |
| DELETE | /api/knowledge/{id} | 删除文档（清除向量数据） |
| GET | /api/knowledge/list | 文档列表（分页） |
| GET | /api/chat/history/{conversationId} | 查询对话历史 |
| DELETE | /api/chat/history/{conversationId} | 清空对话历史 |

### ka-webapp（启动模块 + 前端）

- 启动类 KnowledgeAssistantApplication
- application.yml 配置 Ollama/Redis/Server
- 聊天页面 /（index.html）：对话框 + SSE 流式输出 + 新建对话
- 管理页面 /admin.html：文档上传、列表、删除
- 样式：Tailwind CDN

## 错误处理

- 全局 @ControllerAdvice，统一 Result<T> 响应格式
- LLM 调用失败 -> 返回友好提示"AI 服务暂时不可用"
- 文档解析失败 -> 提示具体原因
- Redis 连接失败 -> 启动时检查，快速失败

## 参考来源

- spring-ai-summary 项目的 spring-ai-rag/spring-ai-chat/spring-ai-chat-memory/spring-ai-vector-redis/spring-ai-chat-ollama 模块
