package com.knowledge.assistant.agent.workflow;

/**
 * workflow step interface
 */
public interface WorkflowStep {

    /**
     * execute step
     *
     * @param input input data
     * @return step result
     */
    String execute(String input);

    /**
     * get step name
     *
     * @return step name
     */
    String name();
}
