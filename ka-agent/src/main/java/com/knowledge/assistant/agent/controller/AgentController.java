package com.knowledge.assistant.agent.controller;

import com.knowledge.assistant.agent.config.AgentWorkflowConfig;
import com.knowledge.assistant.agent.workflow.Workflow;
import com.knowledge.assistant.agent.workflow.WorkflowRequest;
import com.knowledge.assistant.agent.workflow.WorkflowResponse;
import com.knowledge.assistant.common.dto.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Agent workflow controller
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentWorkflowConfig agentWorkflowConfig;

    /**
     * Chain workflow: sequential content generation (outline -> expand -> polish)
     */
    @PostMapping("/chain")
    public Result<WorkflowResponse> chain(@RequestBody WorkflowRequest request) {
        Workflow workflow = agentWorkflowConfig.buildContentChain();
        WorkflowResponse response = workflow.execute(request);
        return response.isSuccess() ? Result.ok(response) : Result.fail(response.getErrorMessage());
    }

    /**
     * Parallel workflow: multi-perspective product review
     */
    @PostMapping("/parallel")
    public Result<WorkflowResponse> parallel(@RequestBody WorkflowRequest request) {
        Workflow workflow = agentWorkflowConfig.buildReviewParallel();
        WorkflowResponse response = workflow.execute(request);
        return response.isSuccess() ? Result.ok(response) : Result.fail(response.getErrorMessage());
    }

    /**
     * Routing workflow: customer service routing
     */
    @PostMapping("/route")
    public Result<WorkflowResponse> route(@RequestBody WorkflowRequest request) {
        Workflow workflow = agentWorkflowConfig.buildCustomerServiceRouting();
        WorkflowResponse response = workflow.execute(request);
        return response.isSuccess() ? Result.ok(response) : Result.fail(response.getErrorMessage());
    }
}
