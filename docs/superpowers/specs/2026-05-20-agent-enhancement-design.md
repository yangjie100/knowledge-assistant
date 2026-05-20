# Day 5: Agent Enhancement - Function Calling + ReAct Reasoning

> Date: 2026-05-20
> Project: knowledge-assistant
> Module: ka-agent

---

## Goal

Add Function Calling to ka-agent, enabling the Agent to invoke Java tool methods.
Implement ReAct reasoning loop with Thought->Action->Observation step recording.
Support SSE streaming for the reasoning process.

## Current State

ka-agent has Chain/Parallel/Routing Workflow patterns but no dynamic tool calling.
Spring AI 1.1.4 provides mature Tool Calling API (@Tool + ToolCallAdvisor + ToolCallingManager).

## Architecture: ToolCallAdvisor Base + ReActWorkflow Wrapper

```
User Request -> AgentController
  -> ReActAgent (custom, step recording + SSE push)
    -> ChatClient (with ToolCallAdvisor)
      -> @Tool methods (knowledge search, datetime, stats, calculator)
```

- ToolCallAdvisor: handles tool matching, parameter parsing, error handling
- ReActAgent: wraps observability (step recording) and streaming (SSE)

---

## 1. Tool Definitions

New package `agent/tools/` in ka-agent with 4 tool classes, all @Component + @Tool.

### 1.1 KnowledgeSearchTool

- Dependency: ka-rag RetrievalService (existing)
- Method: searchKnowledge(String query) -> formatted search results

### 1.2 DateTimeTool

- No external dependency
- Method: getCurrentDateTime() -> ISO-8601 datetime string

### 1.3 KnowledgeStatsTool

- Dependency: Spring AI VectorStore (existing via ka-rag)
- Method: getStats() -> document count and chunk statistics
- Does NOT depend on ka-admin to avoid circular dependency

### 1.4 CalculatorTool

- No external dependency
- Method: calculate(String expression) -> calculation result
- Security: whitelist character validation (digits, operators, parentheses, decimal point, spaces only)

---

## 2. ReActAgent

### 2.1 Core Class

- ChatClient with ToolCallAdvisor + 4 tools
- maxIterations = 10 (prevent infinite loops)
- ReAct system prompt (Chinese, instructs tool usage)
- Synchronous: execute(ReActRequest) -> ReActResponse
- Streaming: streamExecute(ReActRequest) -> Flux<String>

### 2.2 SSE Event Protocol

```
event: thinking   -> {"phase":"reasoning","content":"..."}
event: tool_call  -> {"phase":"tool_call","tool":"...","input":"..."}
event: tool_result -> {"phase":"tool_result","tool":"...","output":"..."}
event: answer     -> {"phase":"answer","content":"..."}
```

---

## 3. API Endpoints

Append to existing AgentController:

- POST /api/agent/react - synchronous ReAct call
- POST /api/agent/react/stream - SSE streaming ReAct call

Both accept ReActRequest { String question }.

---

## 4. Data Models

- ReActRequest: record with String question
- ReActResponse: content, List<AgentStep> steps, boolean success, String errorMessage
- AgentStep: phase, tool, input, output, content, timestamp

---

## 5. New Files

Source (~10 files):
- agent/tools/KnowledgeSearchTool.java
- agent/tools/DateTimeTool.java
- agent/tools/KnowledgeStatsTool.java
- agent/tools/CalculatorTool.java
- agent/react/ReActAgent.java
- agent/react/ReActRequest.java
- agent/react/ReActResponse.java
- agent/react/AgentStep.java
- AgentController.java (append ~30 lines)

Tests (~5 files):
- agent/tools/KnowledgeSearchToolTest.java
- agent/tools/CalculatorToolTest.java
- agent/tools/DateTimeToolTest.java
- agent/tools/KnowledgeStatsToolTest.java
- agent/react/ReActAgentTest.java

---

## 6. Dependencies

No new Maven dependencies needed.
RetrievalService and VectorStore are already available via ka-rag (existing dependency).

---

## 7. Test Strategy

- KnowledgeSearchToolTest: mock RetrievalService, verify empty/non-empty formatting
- CalculatorToolTest: normal ops, illegal char rejection, division by zero, overflow
- DateTimeToolTest: non-null, ISO format
- KnowledgeStatsToolTest: mock VectorStore
- ReActAgentTest: mock ChatClient, verify step recording, maxIterations

---

## 8. Implementation Order

1. Define 4 Tool classes + unit tests
2. Implement ReActAgent synchronous call
3. Implement ReActAgent streaming SSE
4. Append AgentController endpoints
5. ReActAgent integration test
6. End-to-end verification (curl tests)
7. Frontend integration (if needed)

---

## 9. Risks

| Risk | Mitigation |
|------|------------|
| deepseek-r1:32b Function Calling capability limited | Verify Ollama version supports tool calling (0.2.8+) |
| ToolCallAdvisor internal steps hard to observe | Use Advisor chain interception in Spring AI 1.1.4 |
| CalculatorTool security risk | Strict whitelist character validation |
| SSE intermediate step access | May need Advisor pattern to intercept ToolCallAdvisor output |
