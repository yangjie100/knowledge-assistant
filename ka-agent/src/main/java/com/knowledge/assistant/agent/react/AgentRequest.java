package com.knowledge.assistant.agent.react;

import jakarta.validation.constraints.NotBlank;

public record AgentRequest(
        @NotBlank(message = "question must not be blank")
        String question) {
}
