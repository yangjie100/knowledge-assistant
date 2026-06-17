package com.knowledge.assistant.rag.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.assistant.rag.config.RerankerConfig;
import com.knowledge.assistant.rag.rerank.RerankService;
import com.knowledge.assistant.rag.util.ChineseTokenizer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridRetrievalServiceTest {

    @Mock private VectorStore vectorStore;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RerankService rerankService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RerankerConfig rerankerConfig = new RerankerConfig();

    private HybridRetrievalService createService() {
        return new HybridRetrievalService(vectorStore, redisTemplate, objectMapper, rerankerConfig, rerankService, new ChineseTokenizer());
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

    @Test
    void hybridRetrieveAppliesRerankWhenEnabled() {
        rerankerConfig.setEnabled(true);
        rerankerConfig.setRecallTopK(3);
        List<Document> vectorDocs = List.of(
                new Document("v0", "content0", new HashMap<>(Map.of("docId", "d0"))),
                new Document("v1", "content1", new HashMap<>(Map.of("docId", "d1"))));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(vectorDocs);
        when(rerankService.rerank(any(), eq("query"))).thenReturn(List.of(vectorDocs.get(1)));

        List<Document> result = createService().hybridRetrieve("query");

        assertThat(result).containsExactly(vectorDocs.get(1));
        verify(rerankService).rerank(any(), eq("query"));
    }

    @Test
    void hybridRetrieveSkipsRerankWhenDisabled() {
        // enabled defaults to false -> original fused.stream().limit(TOP_K) path
        List<Document> vectorDocs = List.of(
                new Document("v0", "content0", new HashMap<>(Map.of("docId", "d0"))));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(vectorDocs);

        List<Document> result = createService().hybridRetrieve("query");

        verify(rerankService, never()).rerank(any(), any());
        assertThat(result).isNotEmpty();
    }
}
