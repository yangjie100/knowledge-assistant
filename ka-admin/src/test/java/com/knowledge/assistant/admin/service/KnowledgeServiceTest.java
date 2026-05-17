package com.knowledge.assistant.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.knowledge.assistant.common.model.KnowledgeDocument;
import com.knowledge.assistant.rag.service.EmbedResult;
import com.knowledge.assistant.rag.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private KnowledgeService createService() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        return new KnowledgeService(embeddingService, redisTemplate, objectMapper);
    }

    @Test
    void uploadDocument() {
        when(embeddingService.embed(any(byte[].class), eq("test.txt")))
                .thenReturn(new EmbedResult("doc-123", 5));
        KnowledgeService service = createService();

        KnowledgeDocument doc = service.upload("content".getBytes(StandardCharsets.UTF_8), "test.txt");

        assertThat(doc).isNotNull();
        assertThat(doc.getId()).isEqualTo("doc-123");
        assertThat(doc.getTitle()).isEqualTo("test.txt");
        assertThat(doc.getSourceType()).isEqualTo("txt");
        assertThat(doc.getChunkCount()).isEqualTo(5);
        assertThat(doc.getCreateTime()).isNotNull();
        verify(valueOps).set(eq("doc:meta:doc-123"), anyString());
        verify(setOps).add("doc:index", "doc-123");
    }

    @Test
    void uploadDocumentWithoutExtension() {
        when(embeddingService.embed(any(byte[].class), eq("README")))
                .thenReturn(new EmbedResult("doc-456", 1));
        KnowledgeService service = createService();

        KnowledgeDocument doc = service.upload("data".getBytes(StandardCharsets.UTF_8), "README");

        assertThat(doc.getSourceType()).isEqualTo("unknown");
    }

    @Test
    void deleteDocument() {
        when(embeddingService.embed(any(byte[].class), eq("test.txt")))
                .thenReturn(new EmbedResult("doc-789", 3));
        KnowledgeService service = createService();
        service.upload("content".getBytes(StandardCharsets.UTF_8), "test.txt");

        service.delete("doc-789");

        verify(embeddingService).deleteByDocId("doc-789");
        verify(redisTemplate).delete("doc:meta:doc-789");
    }

    @Test
    void listDocuments() {
        when(embeddingService.embed(any(byte[].class), eq("a.txt")))
                .thenReturn(new EmbedResult("id-1", 2));
        when(embeddingService.embed(any(byte[].class), eq("b.pdf")))
                .thenReturn(new EmbedResult("id-2", 4));
        KnowledgeService service = createService();
        service.upload("a".getBytes(StandardCharsets.UTF_8), "a.txt");
        service.upload("b".getBytes(StandardCharsets.UTF_8), "b.pdf");

        // Mock list to return from Redis
        when(setOps.members("doc:index")).thenReturn(Set.of("id-1", "id-2"));
        when(valueOps.get("doc:meta:id-1"))
                .thenReturn("{\"id\":\"id-1\",\"title\":\"a.txt\",\"sourceType\":\"txt\",\"chunkCount\":2,\"fileSize\":1,\"createTime\":\"2026-05-17T12:00:00\"}");
        when(valueOps.get("doc:meta:id-2"))
                .thenReturn("{\"id\":\"id-2\",\"title\":\"b.pdf\",\"sourceType\":\"pdf\",\"chunkCount\":4,\"fileSize\":1,\"createTime\":\"2026-05-17T12:00:01\"}");

        List<KnowledgeDocument> docs = service.list();

        assertThat(docs).hasSize(2);
        assertThat(docs.stream().map(KnowledgeDocument::getId))
                .containsExactlyInAnyOrder("id-1", "id-2");
    }

    @Test
    void listReturnsEmptyWhenNoDocuments() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        KnowledgeService service = new KnowledgeService(embeddingService, redisTemplate, objectMapper);

        List<KnowledgeDocument> docs = service.list();

        assertThat(docs).isEmpty();
    }

    @Test
    void batchDeleteDocuments() {
        when(embeddingService.embed(any(byte[].class), eq("a.txt")))
                .thenReturn(new EmbedResult("id-1", 2));
        when(embeddingService.embed(any(byte[].class), eq("b.txt")))
                .thenReturn(new EmbedResult("id-2", 3));
        KnowledgeService service = createService();
        service.upload("a".getBytes(StandardCharsets.UTF_8), "a.txt");
        service.upload("b".getBytes(StandardCharsets.UTF_8), "b.txt");

        service.deleteBatch(List.of("id-1", "id-2"));

        verify(redisTemplate).delete("doc:meta:id-1");
        verify(redisTemplate).delete("doc:meta:id-2");
        verify(embeddingService).deleteByDocId("id-1");
        verify(embeddingService).deleteByDocId("id-2");
    }

    @Test
    void statsReturnsCorrectCounts() {
        when(setOps.members("doc:index")).thenReturn(Set.of("id-1", "id-2"));
        when(valueOps.get("doc:meta:id-1"))
                .thenReturn("{\"id\":\"id-1\",\"chunkCount\":2,\"createTime\":\"2026-05-17T12:00:00\"}");
        when(valueOps.get("doc:meta:id-2"))
                .thenReturn("{\"id\":\"id-2\",\"chunkCount\":5,\"createTime\":\"2026-05-17T12:00:01\"}");
        KnowledgeService service = createService();

        assertThat(service.getTotalCount()).isEqualTo(2);
        assertThat(service.getTotalChunks()).isEqualTo(7);
    }
}
