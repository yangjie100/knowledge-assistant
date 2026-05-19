package com.knowledge.assistant.rag.service;

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

    @Test
    void embedDocument() {
        Document doc = new Document("test content");
        when(loaderFactory.load(any(byte[].class), eq("test.txt"))).thenReturn(List.of(doc));
        when(splitter.split(anyList())).thenReturn(List.of(doc));
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(valueOperations.get(startsWith("doc:hash:"))).thenReturn(null);

        EmbeddingService service = new EmbeddingService(vectorStore, loaderFactory, splitter, redisTemplate);
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

        EmbeddingService service = new EmbeddingService(vectorStore, loaderFactory, splitter, redisTemplate);
        EmbedResult result = service.embed("duplicate".getBytes(StandardCharsets.UTF_8), "dup.txt");

        assertThat(result.docId()).isEqualTo("existing-doc-id");
        assertThat(result.chunkCount()).isEqualTo(0);
        assertThat(result.duplicate()).isTrue();
        verifyNoInteractions(vectorStore);
        verifyNoInteractions(loaderFactory);
    }
}
