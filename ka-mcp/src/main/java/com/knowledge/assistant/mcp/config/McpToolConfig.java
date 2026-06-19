package com.knowledge.assistant.mcp.config;

import com.knowledge.assistant.agent.tools.CalculatorTool;
import com.knowledge.assistant.agent.tools.DateTimeTool;
import com.knowledge.assistant.agent.tools.KnowledgeSearchTool;
import com.knowledge.assistant.agent.tools.KnowledgeStatsTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the ka-agent {@code @Tool} beans as a single {@link ToolCallbackProvider}.
 *
 * <p>Spring AI MCP server auto-configuration detects every ToolCallbackProvider bean and
 * converts its callbacks into MCP tool specifications exposed over the SSE transport.
 * {@link MethodToolCallbackProvider} reflects each bean for {@code @Tool}-annotated methods —
 * the same mechanism ChatClient.defaultTools(Object...) uses internally.
 *
 * <p>NOTE: {@code MethodToolCallbackProvider.builder().toolObjects(...)} is the standard,
 * version-stable way to wrap {@code @Tool} beans into a ToolCallbackProvider across Spring AI
 * 1.0.x and 1.1.x (this project resolves 1.1.4). Prefer it over {@code ToolCallbacks.from(...)},
 * whose package location has shifted between releases, to keep the module portable.
 *
 * <p>Concrete bean types are injected to keep the exposed tool set explicit. Adding a new
 * {@code @Tool} method on an existing bean needs no change here; adding a new tool bean only
 * requires appending its parameter to {@code toolObjects(...)}.
 */
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider kaMcpTools(DateTimeTool dateTimeTool,
                                           CalculatorTool calculatorTool,
                                           KnowledgeStatsTool statsTool,
                                           KnowledgeSearchTool searchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(dateTimeTool, calculatorTool, statsTool, searchTool)
                .build();
    }
}
