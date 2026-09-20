package com.knowledge.assistant.graph.workflow;

import com.knowledge.assistant.agent.workflow.WorkflowStep;
import com.knowledge.assistant.graph.model.GraphRetrievalResult;
import com.knowledge.assistant.graph.service.GraphRetrievalService;
import lombok.extern.slf4j.Slf4j;

/**
 * Workflow step that runs GraphRAG 3-step retrieval (vector recall -> subgraph expansion ->
 * context assembly) and feeds the assembled context downstream for a ContextAnswerStep to
 * answer from. Mirrors KnowledgeSearchStep, but swaps plain vector retrieval for the
 * graph-augmented variant that can surface multi-hop relations a vector store cannot.
 */
@Slf4j
public class GraphRetrievalStep implements WorkflowStep {

    private final GraphRetrievalService graphRetrievalService;
    private final String stepName;

    public GraphRetrievalStep(GraphRetrievalService graphRetrievalService, String stepName) {
        this.graphRetrievalService = graphRetrievalService;
        this.stepName = stepName;
    }

    @Override
    public String name() {
        return stepName;
    }

    @Override
    public String execute(String input) {
        log.info("GraphRetrievalStep '{}' retrieving for: {}", stepName,
                input.substring(0, Math.min(input.length(), 50)));
        GraphRetrievalResult result = graphRetrievalService.retrieve(input);
        String context = result.toContext();
        if (context == null || context.isBlank()) {
            return "[No graph context found]\n\nQuestion: " + input;
        }
        log.info("GraphRetrievalStep '{}' assembled context, length={}", stepName, context.length());
        return "GraphRAG retrieved context:\n" + context + "\n\nQuestion: " + input;
    }
}
