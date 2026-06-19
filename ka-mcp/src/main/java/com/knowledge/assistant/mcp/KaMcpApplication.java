package com.knowledge.assistant.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the standalone KA MCP Server.
 *
 * <p>scanBasePackages spans the whole {@code com.knowledge.assistant} root so the
 * {@code @Tool} beans in {@code ka-agent.tools} (and the RAG services they depend on
 * in {@code ka-rag}) are picked up. {@link com.knowledge.assistant.mcp.config.McpToolConfig}
 * then wraps them into a ToolCallbackProvider that the MCP server auto-configuration
 * exposes over SSE (/sse + /mcp/message).
 *
 * <p>Shares the same RAG infrastructure as ka-webapp (Redis/GLM/Ollama) via the same
 * application.yml keys; only server.port differs (8090, to avoid clashing with ka-webapp 8080
 * and the 8081 Docker Desktop forward).
 */
@SpringBootApplication(scanBasePackages = "com.knowledge.assistant")
public class KaMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(KaMcpApplication.class, args);
    }
}
