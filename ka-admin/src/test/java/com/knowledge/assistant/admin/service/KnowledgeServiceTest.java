package com.knowledge.assistant.admin.service;

import com.knowledge.assistant.common.model.KnowledgeDocument;
import com.knowledge.assistant.rag.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {
    @Mock
    private EmbeddingService embeddingService;

    @Test
    void uploadDocument() {
        when(embeddingService.embed(any(byte[].class), eq("test.txt")))
                .thenReturn("doc-123");
        KnowledgeService service = new KnowledgeService(embeddingService);

        KnowledgeDocument doc = service.upload("content".getBytes(StandardCharsets.UTF_8), "test.txt");

        assertThat(doc).isNotNull();
        assertThat(doc.getId()).isEqualTo("doc-123");
        assertThat(doc.getTitle()).isEqualTo("test.txt");
        assertThat(doc.getSourceType()).isEqualTo("txt");
        assertThat(doc.getCreateTime()).isNotNull();
        verify(embeddingService).embed(any(byte[].class), eq("test.txt"));
    }

    @Test
    void uploadDocumentWithoutExtension() {
        when(embeddingService.embed(any(byte[].class), eq("README")))
                .thenReturn("doc-456");
        KnowledgeService service = new KnowledgeService(embeddingService);

        KnowledgeDocument doc = service.upload("data".getBytes(StandardCharsets.UTF_8), "README");

        assertThat(doc.getSourceType()).isEqualTo("unknown");
    }

    @Test
    void deleteDocument() {
        when(embeddingService.embed(any(byte[].class), eq("test.txt")))
                .thenReturn("doc-789");
        KnowledgeService service = new KnowledgeService(embeddingService);
        service.upload("content".getBytes(StandardCharsets.UTF_8), "test.txt");

        service.delete("doc-789");

        verify(embeddingService).deleteByDocId("doc-789");
        assertThat(service.list()).isEmpty();
    }

    @Test
    void listDocuments() {
        when(embeddingService.embed(any(byte[].class), eq("a.txt")))
                .thenReturn("id-1");
        when(embeddingService.embed(any(byte[].class), eq("b.pdf")))
                .thenReturn("id-2");
        KnowledgeService service = new KnowledgeService(embeddingService);
        service.upload("a".getBytes(StandardCharsets.UTF_8), "a.txt");
        service.upload("b".getBytes(StandardCharsets.UTF_8), "b.pdf");

        List<KnowledgeDocument> docs = service.list();

        assertThat(docs).hasSize(2);
        assertThat(docs.stream().map(KnowledgeDocument::getId))
                .containsExactlyInAnyOrder("id-1", "id-2");
    }

    @Test
    void listReturnsEmptyWhenNoDocuments() {
        KnowledgeService service = new KnowledgeService(embeddingService);

        List<KnowledgeDocument> docs = service.list();

        assertThat(docs).isEmpty();
    }
}
