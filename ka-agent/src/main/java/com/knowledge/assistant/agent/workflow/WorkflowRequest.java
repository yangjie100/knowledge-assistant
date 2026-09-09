package com.knowledge.assistant.agent.workflow;

import jakarta.validation.constraints.NotBlank;

/**
 * workflow request
 */
public record WorkflowRequest(
        @NotBlank(message = "question must not be blank")
        String question) {
}
