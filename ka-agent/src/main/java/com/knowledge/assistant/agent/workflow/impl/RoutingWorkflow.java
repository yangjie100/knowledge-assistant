package com.knowledge.assistant.agent.workflow.impl;

import com.knowledge.assistant.agent.workflow.Workflow;
import com.knowledge.assistant.agent.workflow.WorkflowRequest;
import com.knowledge.assistant.agent.workflow.WorkflowResponse;
import com.knowledge.assistant.agent.workflow.WorkflowStep;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Routing workflow: uses a router step to classify input, then dispatches to the matching step.
 * Implements the Routing pattern (Anthropic, "Building Effective Agents").
 */
@Slf4j
public class RoutingWorkflow implements Workflow {

    private final WorkflowStep router;
    private final Map<String, WorkflowStep> stepMap;

    public RoutingWorkflow(WorkflowStep router, Map<String, WorkflowStep> stepMap) {
        this.router = router;
        this.stepMap = stepMap;
    }

    @Override
    public WorkflowResponse execute(WorkflowRequest input) {
        try {
            log.info("Routing workflow started, route count: {}", stepMap.size());

            String routeKey = router.execute(input.question()).trim();
            log.info("Route selected: {}", routeKey);

            WorkflowStep step = stepMap.get(routeKey);
            if (step == null) {
                log.warn("No step found for route: {}", routeKey);
                return WorkflowResponse.builder()
                        .success(false)
                        .errorMessage("No step found for route: " + routeKey)
                        .build();
            }

            log.info("Executing step: {}", step.name());
            String result = step.execute(input.question());
            log.info("Routing workflow completed");

            return WorkflowResponse.builder()
                    .content(result)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Routing workflow failed", e);
            return WorkflowResponse.builder()
                    .success(false)
                    .errorMessage("Routing workflow failed: " + e.getMessage())
                    .build();
        }
    }
}
