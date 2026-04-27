package com.knowledge.assistant.agent.workflow;

/**
 * workflow core interface
 */
public interface Workflow {

    /**
     * execute workflow
     *
     * @param input workflow request
     * @return workflow response
     */
    WorkflowResponse execute(WorkflowRequest input);
}
