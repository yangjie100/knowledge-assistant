package com.knowledge.assistant.common.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeDocument {
    private String id;
    private String title;
    private String sourceType;
    private int chunkCount;
    private long fileSize;
    private LocalDateTime createTime;
    private String contentHash;
    private int wordCount;
    private String language;
    private String status;
}
