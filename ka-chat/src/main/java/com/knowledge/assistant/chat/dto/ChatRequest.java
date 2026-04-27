package com.knowledge.assistant.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
    @NotBlank(message = "question must not be blank")
    @Size(max = 2000, message = "question too long")
    String question,
    String conversationId
) {}
