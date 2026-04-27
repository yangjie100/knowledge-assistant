package com.knowledge.assistant.agent.workflow.impl;

import com.knowledge.assistant.agent.workflow.Workflow;
import com.knowledge.assistant.agent.workflow.WorkflowRequest;
import com.knowledge.assistant.agent.workflow.WorkflowResponse;
import com.knowledge.assistant.agent.workflow.WorkflowStep;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Chain workflow: steps execute sequentially, output of each step feeds into the next
 */
@Slf4j
public class ChainWorkflow implements Workflow {

    private final List<WorkflowStep> steps;

    public ChainWorkflow(List<WorkflowStep> steps) {
        this.steps = steps;
    }

    @Override
    public WorkflowResponse execute(WorkflowRequest input) {
        String current = input.question();

        try {
            log.info("Chain workflow started, step count: {}", steps.size());

            for (WorkflowStep step : steps) {
                log.info("Executing step: {}, input length: {}", step.name(), current.length());
                current = step.execute(current);
            }

            log.info("Chain workflow completed");
            return WorkflowResponse.builder()
                    .content(current)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Chain workflow failed", e);
            return WorkflowResponse.builder()
                    .success(false)
                    .errorMessage("Chain workflow failed: " + e.getMessage())
                    .build();
        }
    }
}
