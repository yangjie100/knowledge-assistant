package com.knowledge.assistant.agent.workflow;

import com.knowledge.assistant.rag.service.RetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class KnowledgeSearchStep implements WorkflowStep {

    private final RetrievalService retrievalService;
    private final String stepName;

    public KnowledgeSearchStep(RetrievalService retrievalService, String stepName) {
        this.retrievalService = retrievalService;
        this.stepName = stepName;
    }

    @Override
    public String name() {
        return stepName;
    }

    @Override
    public String execute(String input) {
        log.info("KnowledgeSearchStep '{}' searching for: {}", stepName, input.substring(0, Math.min(input.length(), 50)));
        List<Document> docs = retrievalService.retrieve(input);
        if (docs.isEmpty()) {
            return "[No relevant documents found]\n\nQuestion: " + input;
        }
        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
        log.info("KnowledgeSearchStep '{}' found {} documents", stepName, docs.size());
        return "Retrieved context:\n" + context + "\n\nQuestion: " + input;
    }
}
