# Agent Enhancement Implementation Plan

**Goal:** Add Function Calling, ReAct reasoning, and SSE streaming to ka-agent.
**Architecture:** ToolCallAdvisor base + custom ReActAgent wrapper for observability/streaming.
**Tech Stack:** Spring AI 1.1.4 @Tool/ToolCallbacks/ToolCallAdvisor, Reactor Flux SSE.

## Tasks (in order)

1. Data Models: ReActRequest, ReActResponse, AgentStep
2. CalculatorTool + test (safe math parser)
3. DateTimeTool + test
4. KnowledgeSearchTool + test (uses RetrievalService)
5. KnowledgeStatsTool + test
6. ReActAgentConfig (wire ToolCallAdvisor + 4 tools + ReActAgent bean)
7. ReActAgent (sync execute + Flux streamExecute) + test
8. AgentController: add POST /react and POST /react/stream
9. Full test suite + fix imports
10. End-to-end verification

## Key Import Paths (Spring AI 1.1.4)

- org.springframework.ai.tool.annotation.Tool
- org.springframework.ai.tool.annotation.ToolParam
- org.springframework.ai.tool.ToolCallbacks
- org.springframework.ai.tool.ToolCallingManager
- org.springframework.ai.tool.advisor.ToolCallAdvisor (or org.springframework.ai.tool.ToolCallAdvisor)

See spec: docs/superpowers/specs/2026-05-20-agent-enhancement-design.md
