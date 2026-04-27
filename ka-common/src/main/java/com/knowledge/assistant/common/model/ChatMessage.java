package com.knowledge.assistant.common.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessage {
    private String role;
    private String content;
    private LocalDateTime timestamp;
}
