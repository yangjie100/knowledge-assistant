package com.knowledge.assistant.rag.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {
    @Mock private HybridRetrievalService hybridRetrievalService;

    @Test
    void retrieveReturnsDocuments() {
        when(hybridRetrievalService.hybridRetrieve("query")).thenReturn(List.of(new Document("content")));
        RetrievalService service = new RetrievalService(hybridRetrievalService);
        List<Document> results = service.retrieve("query");
        assertThat(results).hasSize(1);
    }

    @Test
    void retrieveReturnsEmpty() {
        when(hybridRetrievalService.hybridRetrieve("no match")).thenReturn(List.of());
        RetrievalService service = new RetrievalService(hybridRetrievalService);
        assertThat(service.retrieve("no match")).isEmpty();
    }
}
