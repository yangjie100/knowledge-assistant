package com.knowledge.assistant.agent.controller;

import com.knowledge.assistant.agent.config.AgentWorkflowConfig;
import com.knowledge.assistant.agent.react.ReActAgent;
import com.knowledge.assistant.agent.react.ReActRequest;
import com.knowledge.assistant.agent.react.ReActResponse;
import com.knowledge.assistant.agent.workflow.Workflow;
import com.knowledge.assistant.agent.workflow.WorkflowRequest;
import com.knowledge.assistant.agent.workflow.WorkflowResponse;
import com.knowledge.assistant.common.dto.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentWorkflowConfig agentWorkflowConfig;
    private final ReActAgent reActAgent;

    @PostMapping("/chain")
    public Result<WorkflowResponse> chain(@RequestBody WorkflowRequest request) {
        Workflow workflow = agentWorkflowConfig.buildContentChain();
        WorkflowResponse response = workflow.execute(request);
        return response.isSuccess() ? Result.ok(response) : Result.fail(response.getErrorMessage());
    }

    @PostMapping("/parallel")
    public Result<WorkflowResponse> parallel(@RequestBody WorkflowRequest request) {
        Workflow workflow = agentWorkflowConfig.buildReviewParallel();
        WorkflowResponse response = workflow.execute(request);
        return response.isSuccess() ? Result.ok(response) : Result.fail(response.getErrorMessage());
    }

    @PostMapping("/route")
    public Result<WorkflowResponse> route(@RequestBody WorkflowRequest request) {
        Workflow workflow = agentWorkflowConfig.buildCustomerServiceRouting();
        WorkflowResponse response = workflow.execute(request);
        return response.isSuccess() ? Result.ok(response) : Result.fail(response.getErrorMessage());
    }

    @PostMapping("/rag-chain")
    public Result<WorkflowResponse> ragChain(@RequestBody WorkflowRequest request) {
        Workflow workflow = agentWorkflowConfig.buildRagChain();
        WorkflowResponse response = workflow.execute(request);
        return response.isSuccess() ? Result.ok(response) : Result.fail(response.getErrorMessage());
    }

    @PostMapping("/rag-route")
    public Result<WorkflowResponse> ragRoute(@RequestBody WorkflowRequest request) {
        Workflow workflow = agentWorkflowConfig.buildRagRouting();
        WorkflowResponse response = workflow.execute(request);
        return response.isSuccess() ? Result.ok(response) : Result.fail(response.getErrorMessage());
    }

    @PostMapping("/react")
    public Result<ReActResponse> react(@RequestBody ReActRequest request) {
        ReActResponse response = reActAgent.execute(request);
        return response.isSuccess() ? Result.ok(response) : Result.fail(response.getErrorMessage());
    }

    @PostMapping(value = "/react/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> reactStream(@RequestBody ReActRequest request) {
        return reActAgent.streamExecute(request);
    }
}
