package com.knowledge.assistant.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.assistant.rag.loader.DocumentLoaderFactory;
import com.knowledge.assistant.rag.splitter.DocumentSplitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.HashOperations;
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
class EmbeddingServiceTest {
    @Mock private VectorStore vectorStore;
    @Mock private DocumentLoaderFactory loaderFactory;
    @Mock private DocumentSplitter splitter;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SetOperations<String, String> setOperations;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void embedDocument() {
        Document doc = new Document("test content");
        when(loaderFactory.load(any(byte[].class), eq("test.txt"))).thenReturn(List.of(doc));
        when(splitter.split(anyList())).thenReturn(List.of(doc));
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(valueOperations.get(startsWith("doc:hash:"))).thenReturn(null);

        EmbeddingService service = new EmbeddingService(vectorStore, loaderFactory, splitter, redisTemplate, objectMapper);
        EmbedResult result = service.embed("test content".getBytes(StandardCharsets.UTF_8), "test.txt");

        assertThat(result.docId()).isNotNull();
        assertThat(result.chunkCount()).isEqualTo(1);
        assertThat(result.duplicate()).isFalse();
        verify(vectorStore).add(anyList());
        verify(setOperations).add(startsWith("doc:chunks:"), any(String[].class));
    }

    @Test
    void embedDuplicateDocumentReturnsExistingId() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(startsWith("doc:hash:"))).thenReturn("existing-doc-id");

        EmbeddingService service = new EmbeddingService(vectorStore, loaderFactory, splitter, redisTemplate, objectMapper);
        EmbedResult result = service.embed("duplicate".getBytes(StandardCharsets.UTF_8), "dup.txt");

        assertThat(result.docId()).isEqualTo("existing-doc-id");
        assertThat(result.chunkCount()).isEqualTo(0);
        assertThat(result.duplicate()).isTrue();
        verifyNoInteractions(vectorStore);
        verifyNoInteractions(loaderFactory);
    }

    @Test
    void deleteByDocIdRemovesContentHashIndexKey() {
        // H-2: doc:hash:<sha256> must be cleaned on delete, otherwise re-uploading
        // identical content is forever flagged DUPLICATE with chunkCount=0.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("doc:meta:doc-1"))
                .thenReturn("{\"id\":\"doc-1\",\"contentHash\":\"abc123\"}");
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("doc:chunks:doc-1")).thenReturn(Set.of("chunk-1"));

        EmbeddingService service = new EmbeddingService(vectorStore, loaderFactory, splitter, redisTemplate, objectMapper);
        service.deleteByDocId("doc-1");

        verify(redisTemplate).delete("doc:hash:abc123");
        verify(redisTemplate).delete("chunk:text:chunk-1");
        verify(redisTemplate).delete("doc:chunks:doc-1");
    }

    @Test
    void deleteByDocIdToleratesMissingMeta() {
        // Legacy docs may lack meta or contentHash — deletion must not break, and no
        // doc:hash key deletion should be attempted blindly.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("doc:meta:doc-2")).thenReturn(null);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("doc:chunks:doc-2")).thenReturn(Set.of());

        EmbeddingService service = new EmbeddingService(vectorStore, loaderFactory, splitter, redisTemplate, objectMapper);
        service.deleteByDocId("doc-2");

        verify(redisTemplate, never()).delete(startsWith("doc:hash:"));
        verify(redisTemplate).delete("doc:chunks:doc-2");
    }
}
