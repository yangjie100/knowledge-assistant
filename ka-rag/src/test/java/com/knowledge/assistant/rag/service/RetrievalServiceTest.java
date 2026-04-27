package com.knowledge.assistant.rag.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {
    @Mock private VectorStore vectorStore;

    @Test
    void retrieveReturnsDocuments() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(new Document("content")));
        RetrievalService service = new RetrievalService(vectorStore);
        List<Document> results = service.retrieve("query");
        assertThat(results).hasSize(1);
    }

    @Test
    void retrieveReturnsEmpty() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        RetrievalService service = new RetrievalService(vectorStore);
        assertThat(service.retrieve("no match")).isEmpty();
    }
}
