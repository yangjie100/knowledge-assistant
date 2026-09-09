package com.knowledge.assistant.agent.controller;

import com.knowledge.assistant.agent.config.AgentWorkflowConfig;
import com.knowledge.assistant.agent.react.AgentExecutor;
import com.knowledge.assistant.agent.react.AgentRequest;
import com.knowledge.assistant.agent.react.AgentResponse;
import com.knowledge.assistant.agent.workflow.Workflow;
import com.knowledge.assistant.agent.workflow.WorkflowRequest;
import com.knowledge.assistant.agent.workflow.WorkflowResponse;
import com.knowledge.assistant.common.dto.Result;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentWorkflowConfig agentWorkflowConfig;
    private final AgentExecutor agentExecutor;

    @PostMapping("/chain")
    public Result<WorkflowResponse> chain(@Valid @RequestBody WorkflowRequest request) {
        Workflow workflow = agentWorkflowConfig.buildContentChain();
        WorkflowResponse response = workflow.execute(request);
        return response.isSuccess() ? Result.ok(response) : Result.fail(response.getErrorMessage());
    }

    @PostMapping("/parallel")
    public Result<WorkflowResponse> parallel(@Valid @RequestBody WorkflowRequest request) {
        Workflow workflow = agentWorkflowConfig.buildReviewParallel();
        WorkflowResponse response = workflow.execute(request);
        return response.isSuccess() ? Result.ok(response) : Result.fail(response.getErrorMessage());
    }

    @PostMapping("/route")
    public Result<WorkflowResponse> route(@Valid @RequestBody WorkflowRequest request) {
        Workflow workflow = agentWorkflowConfig.buildCustomerServiceRouting();
        WorkflowResponse response = workflow.execute(request);
        return response.isSuccess() ? Result.ok(response) : Result.fail(response.getErrorMessage());
    }

    @PostMapping("/rag-chain")
    public Result<WorkflowResponse> ragChain(@Valid @RequestBody WorkflowRequest request) {
        Workflow workflow = agentWorkflowConfig.buildRagChain();
        WorkflowResponse response = workflow.execute(request);
        return response.isSuccess() ? Result.ok(response) : Result.fail(response.getErrorMessage());
    }

    @PostMapping("/rag-route")
    public Result<WorkflowResponse> ragRoute(@Valid @RequestBody WorkflowRequest request) {
        Workflow workflow = agentWorkflowConfig.buildRagRouting();
        WorkflowResponse response = workflow.execute(request);
        return response.isSuccess() ? Result.ok(response) : Result.fail(response.getErrorMessage());
    }

    @PostMapping("/react")
    public Result<AgentResponse> react(@Valid @RequestBody AgentRequest request) {
        AgentResponse response = agentExecutor.execute(request);
        return response.isSuccess() ? Result.ok(response) : Result.fail(response.getErrorMessage());
    }

    @PostMapping(value = "/react/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> reactStream(@Valid @RequestBody AgentRequest request) {
        return agentExecutor.streamExecute(request);
    }
}
