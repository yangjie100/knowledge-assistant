package com.knowledge.assistant.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {
    private final HybridRetrievalService hybridRetrievalService;

    public List<Document> retrieve(String query) {
        log.info("Retrieving documents for query: {}", query);
        return hybridRetrievalService.hybridRetrieve(query);
    }
}
