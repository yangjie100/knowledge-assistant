package com.knowledge.assistant.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class KnowledgeStatsTool {
    @Tool(description = "Get current system status and knowledge base information")
    public String getStats() {
        return "Knowledge Assistant Status: Online\nTimestamp: " + LocalDateTime.now()
                + "\nNote: Detailed stats available via /api/admin/documents";
    }
}
