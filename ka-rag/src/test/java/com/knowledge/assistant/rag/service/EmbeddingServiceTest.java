package com.knowledge.assistant.rag.service;

import com.knowledge.assistant.rag.loader.DocumentLoaderFactory;
import com.knowledge.assistant.rag.splitter.DocumentSplitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
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

    @Test
    void embedDocument() {
        Document doc = new Document("test content");
        when(loaderFactory.load(any(byte[].class), eq("test.txt"))).thenReturn(List.of(doc));
        when(splitter.split(anyList())).thenReturn(List.of(doc));
        EmbeddingService service = new EmbeddingService(vectorStore, loaderFactory, splitter);
        String id = service.embed("test content".getBytes(StandardCharsets.UTF_8), "test.txt");
        assertThat(id).isNotNull();
        verify(vectorStore).add(anyList());
    }
}
