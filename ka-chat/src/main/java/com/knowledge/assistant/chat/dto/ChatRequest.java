package com.knowledge.assistant.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatRequest(
    @NotBlank(message = "question must not be blank")
    @Size(max = 2000, message = "question too long")
    String question,
    @Pattern(regexp = "^[A-Za-z0-9-]{0,64}$",
            message = "conversationId must be alphanumeric/dash, max 64 chars")
    String conversationId
) {}
