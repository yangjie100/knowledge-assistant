package com.knowledge.assistant.agent.workflow;

import lombok.Builder;
import lombok.Data;

/**
 * workflow response
 */
@Data
@Builder
public class WorkflowResponse {

    private String content;

    private boolean success;

    private String errorMessage;
}
