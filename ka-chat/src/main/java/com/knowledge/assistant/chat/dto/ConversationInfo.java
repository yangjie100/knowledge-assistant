package com.knowledge.assistant.chat.dto;

import java.time.LocalDateTime;

public record ConversationInfo(
        String conversationId,
        LocalDateTime createdAt,
        LocalDateTime lastMessageAt,
        int messageCount,
        String lastQuestion
) {}
