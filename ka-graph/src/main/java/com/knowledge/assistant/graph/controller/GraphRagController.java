package com.knowledge.assistant.graph.controller;

import com.knowledge.assistant.agent.workflow.Workflow;
import com.knowledge.assistant.agent.workflow.WorkflowRequest;
import com.knowledge.assistant.agent.workflow.WorkflowResponse;
import com.knowledge.assistant.common.dto.Result;
import com.knowledge.assistant.graph.config.GraphWorkflowConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the GraphRAG chain at POST /api/agent/rag-graph. Conditional on ka.graph.enabled so
 * the endpoint (and its bean dependencies) only materialize when GraphRAG is opted in — the
 * default deployment stays untouched and adds zero overhead.
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ka.graph.enabled", havingValue = "true")
public class GraphRagController {

    private final GraphWorkflowConfig graphWorkflowConfig;

    @PostMapping("/rag-graph")
    public Result<WorkflowResponse> ragGraph(@RequestBody WorkflowRequest request) {
        Workflow workflow = graphWorkflowConfig.buildGraphRagChain();
        WorkflowResponse response = workflow.execute(request);
        return response.isSuccess() ? Result.ok(response) : Result.fail(response.getErrorMessage());
    }
}
