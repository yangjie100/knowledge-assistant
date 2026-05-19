package com.knowledge.assistant.rag.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridRetrievalServiceTest {

    @Mock private VectorStore vectorStore;
    @Mock private StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HybridRetrievalService createService() {
        return new HybridRetrievalService(vectorStore, redisTemplate, objectMapper);
    }

    @Test
    void rrfFusionCombinesVectorAndKeywordResults() {
        Document doc1 = new Document("id1", "vector content", Map.of("docId", "d1"));
        Document doc2 = new Document("id2", "keyword content", Map.of("docId", "d2"));

        HybridRetrievalService service = createService();
        List<Document> result = service.rrfFusion(List.of(doc1), List.of(
            new HybridRetrievalService.KeywordResult("id2", "keyword content", "d2")));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMetadata()).containsKey("rrfScore");
    }

    @Test
    void rrfFusionDeduplicatesById() {
        Document doc = new Document("shared-id", "content", Map.of("docId", "d1"));

        HybridRetrievalService service = createService();
        List<Document> result = service.rrfFusion(List.of(doc), List.of(
            new HybridRetrievalService.KeywordResult("shared-id", "content", "d1")));

        assertThat(result).hasSize(1);
        double score = (double) result.get(0).getMetadata().get("rrfScore");
        assertThat(score).isGreaterThan(0.02);
    }

    @Test
    void rrfFusionEmptyInputs() {
        HybridRetrievalService service = createService();
        List<Document> result = service.rrfFusion(List.of(), List.of());
        assertThat(result).isEmpty();
    }
}
