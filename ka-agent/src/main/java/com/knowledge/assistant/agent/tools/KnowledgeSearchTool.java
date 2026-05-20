package com.knowledge.assistant.agent.tools;

import com.knowledge.assistant.rag.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool {
    private final RetrievalService retrievalService;

    @Tool(description = "Search the knowledge base for relevant information using semantic search")
    public String searchKnowledge(@ToolParam(description = "Natural language search query") String query) {
        List<Document> docs = retrievalService.retrieve(query);
        if (docs.isEmpty()) return "No relevant documents found for: " + query;
        return docs.stream().map(Document::getText).collect(Collectors.joining("\n\n---\n\n"));
    }
}
