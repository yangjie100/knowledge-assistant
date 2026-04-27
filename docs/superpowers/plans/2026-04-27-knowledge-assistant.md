# Knowledge Assistant 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a general-purpose intelligent Q&A system based on Spring AI + Ollama + Redis VectorStore

**Architecture:** Maven multi-module project, 5 modules: common -> rag -> chat -> admin -> webapp

**Tech Stack:** Spring Boot 3.3.6, Spring AI 1.0.0, Java 21, Ollama qwen2.5, Redis VectorStore, Tailwind CDN

---

## Task 1: Project Skeleton - Parent POM + Module POMs

**Files:** pom.xml, ka-common/pom.xml, ka-rag/pom.xml, ka-chat/pom.xml, ka-admin/pom.xml, ka-webapp/pom.xml

- [ ] **Step 1:** Create parent POM (spring-boot-starter-parent 3.3.6, spring-ai-bom 1.0.0, 5 modules)
- [ ] **Step 2:** Create ka-common/pom.xml (pure Java module, no special deps)
- [ ] **Step 3:** Create ka-rag/pom.xml (deps: ka-common, spring-ai-starter-model-ollama, spring-ai-starter-vector-store-redis, spring-ai-pdf-document-reader, spring-ai-markdown-document-reader, spring-ai-jsoup-document-reader)
- [ ] **Step 4:** Create ka-chat/pom.xml (deps: ka-common, ka-rag, spring-ai-starter-model-ollama)
- [ ] **Step 5:** Create ka-admin/pom.xml (deps: ka-chat)
- [ ] **Step 6:** Create ka-webapp/pom.xml (deps: ka-admin, spring-boot-maven-plugin)
- [ ] **Step 7:** Create all module src dirs, run `mvn compile -q` to verify
- [ ] **Step 8:** `git commit -m "feat: project skeleton with 5 maven modules"`

---

## Task 2: ka-common - Result + Models + Exceptions

**Files:** Result.java, KnowledgeDocument.java, ChatMessage.java, AiServiceException.java, DocumentParseException.java
**Test:** ResultTest.java

- [ ] **Step 1:** Write ResultTest (test ok/fail static methods)
- [ ] **Step 2:** Implement Result<T> (code, success, message, data + static factory methods ok/fail)
- [ ] **Step 3:** Implement KnowledgeDocument (id, title, sourceType, chunkCount, createTime)
- [ ] **Step 4:** Implement ChatMessage (role, content, timestamp)
- [ ] **Step 5:** Implement AiServiceException / DocumentParseException
- [ ] **Step 6:** `mvn test -pl ka-common` verify pass
- [ ] **Step 7:** `git commit -m "feat: ka-common with Result, models, exceptions"`

---

## Task 3: ka-rag - Document Loaders + Splitter

**Files:** DocumentLoader.java (interface), TextDocumentLoader, MarkdownDocumentLoader, PdfDocumentLoader, HtmlDocumentLoader, DocumentLoaderFactory, DocumentSplitter
**Test:** TextDocumentLoaderTest, DocumentSplitterTest

- [ ] **Step 1:** Write DocumentLoader interface (`boolean supports(String filename)` + `List<Document> load(byte[], String)`)
- [ ] **Step 2:** Write TextDocumentLoaderTest (supports and load)
- [ ] **Step 3:** Implement TextDocumentLoader (UTF-8 decode, supports .txt)
- [ ] **Step 4:** Implement MarkdownDocumentLoader (same as Text, supports .md)
- [ ] **Step 5:** Implement PdfDocumentLoader (PagePdfDocumentReader, supports .pdf, wrap exceptions as DocumentParseException)
- [ ] **Step 6:** Implement HtmlDocumentLoader (JsoupDocumentReader, supports .html)
- [ ] **Step 7:** Implement DocumentLoaderFactory (iterate 4 loaders, match supports, throw DocumentParseException if none match)
- [ ] **Step 8:** Implement DocumentSplitter (TokenTextSplitter, chunkSize=800, overlap=200)
- [ ] **Step 9:** Write DocumentSplitterTest (long text multi-chunk, short text single chunk)
- [ ] **Step 10:** `mvn test -pl ka-rag` verify pass
- [ ] **Step 11:** `git commit -m "feat: ka-rag document loaders and splitter"`

---

## Task 4: ka-rag - EmbeddingService + RetrievalService

**Files:** EmbeddingService.java, RetrievalService.java
**Test:** EmbeddingServiceTest, RetrievalServiceTest

- [ ] **Step 1:** Write EmbeddingServiceTest (mock VectorStore + DocumentLoaderFactory + DocumentSplitter)
- [ ] **Step 2:** Implement EmbeddingService (load -> split -> add UUID docId metadata -> vectorStore.add)
- [ ] **Step 3:** Write RetrievalServiceTest (mock VectorStore.similaritySearch)
- [ ] **Step 4:** Implement RetrievalService (SearchRequest builder, topK=5, threshold=0.7)
- [ ] **Step 5:** `mvn test -pl ka-rag` verify pass
- [ ] **Step 6:** `git commit -m "feat: ka-rag embedding and retrieval services"`

---

## Task 5: ka-chat - ChatClient + Memory + ChatService + Controller

**Files:** OllamaChatClientConfig.java, ChatService.java, ChatController.java
**Test:** ChatServiceTest.java

- [ ] **Step 1:** Implement OllamaChatClientConfig (InMemoryChatMemory Bean + ChatClient Bean + MessageChatMemoryAdvisor + Chinese system prompt)
- [ ] **Step 2:** Write ChatServiceTest (mock ChatClient + RetrievalService)
- [ ] **Step 3:** Implement ChatService (retrieve -> assemble context -> chatClient.prompt().user().advisors().call().content())
- [ ] **Step 4:** Implement ChatController (POST /api/chat, accepts question + conversationId)
- [ ] **Step 5:** `mvn test -pl ka-chat` verify pass
- [ ] **Step 6:** `git commit -m "feat: ka-chat with ChatClient, memory, ChatService"`

---

## Task 6: ka-admin - Knowledge Management + Global Error Handler

**Files:** KnowledgeService.java, KnowledgeController.java, GlobalExceptionHandler.java
**Test:** KnowledgeControllerTest.java

- [ ] **Step 1:** Implement KnowledgeService (ConcurrentHashMap store, upload/delete/list, calls EmbeddingService)
- [ ] **Step 2:** Implement KnowledgeController (POST /upload, DELETE /{id}, GET /list)
- [ ] **Step 3:** Implement GlobalExceptionHandler (@RestControllerAdvice for DocumentParseException/AiServiceException/Exception)
- [ ] **Step 4:** Write KnowledgeControllerTest (@WebMvcTest + MockMultipartFile)
- [ ] **Step 5:** `mvn test -pl ka-admin` verify pass
- [ ] **Step 6:** `git commit -m "feat: ka-admin with knowledge management API and error handling"`

---

## Task 7: ka-webapp - Application + Config + Frontend

**Files:** KnowledgeAssistantApplication.java, application.yml, static/index.html, static/admin.html

- [ ] **Step 1:** Implement KnowledgeAssistantApplication (@SpringBootApplication)
- [ ] **Step 2:** Implement application.yml (Ollama base-url + qwen2.5 model, Redis localhost:6379/123456, vectorstore.redis initialize-schema/index-name/prefix, multipart 10MB)
- [ ] **Step 3:** Implement index.html chat page (Tailwind CDN, message list, fetch POST /api/chat, new conversation button, link to admin)
- [ ] **Step 4:** Implement admin.html management page (file upload form, fetch POST /api/knowledge/upload, doc list GET /list, delete DELETE /{id})
- [ ] **Step 5:** `mvn compile -q` full compile verification
- [ ] **Step 6:** `git commit -m "feat: ka-webapp with application config and frontend pages"`

---

## Task 8: Full Test + Startup Verification

- [ ] **Step 1:** `mvn test` full test suite
- [ ] **Step 2:** Ensure Ollama and Redis running (`docker start redis && ollama list`)
- [ ] **Step 3:** `mvn spring-boot:run -pl ka-webapp` start application
- [ ] **Step 4:** Functional verification: visit / for chat, /admin.html to upload doc, then chat about doc content
- [ ] **Step 5:** `git commit -m "feat: knowledge-assistant v1.0 complete"`
