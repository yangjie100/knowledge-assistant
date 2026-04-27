package com.knowledge.assistant.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {
    private final VectorStore vectorStore;
    private static final int TOP_K = 5;
    private static final double SIMILARITY_THRESHOLD = 0.7;

    public List<Document> retrieve(String query) {
        log.info("Retrieving documents for query: {}", query);
        return vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build()
        );
    }
}
