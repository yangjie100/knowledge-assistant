package com.knowledge.assistant.rag.service;

public record EmbedResult(String docId, int chunkCount, boolean duplicate) {
    public EmbedResult(String docId, int chunkCount) {
        this(docId, chunkCount, false);
    }
}
