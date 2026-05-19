package com.knowledge.assistant.rag.config;

import com.knowledge.assistant.rag.service.HybridRetrievalService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RetrievalInitializer {

    private final HybridRetrievalService hybridRetrievalService;

    @PostConstruct
    public void init() {
        log.info("Ensuring Redis FT index for hybrid retrieval");
        hybridRetrievalService.ensureFtIndex();
    }
}
