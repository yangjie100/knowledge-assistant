package com.knowledge.assistant.agent.workflow;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ChainStep implements WorkflowStep {

    private final List<WorkflowStep> steps;
    private final String stepName;

    public ChainStep(String stepName, List<WorkflowStep> steps) {
        this.stepName = stepName;
        this.steps = steps;
    }

    @Override
    public String name() {
        return stepName;
    }

    @Override
    public String execute(String input) {
        String current = input;
        for (WorkflowStep step : steps) {
            current = step.execute(current);
        }
        return current;
    }
}
