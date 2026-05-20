package com.knowledge.assistant.agent.tools;

import com.knowledge.assistant.rag.service.RetrievalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeSearchToolTest {
    @Mock
    RetrievalService retrievalService;

    @Test
    void shouldReturnFormattedResults() {
        when(retrievalService.retrieve(anyString())).thenReturn(List.of(
                new Document("Spring AI supports tool calling"),
                new Document("RAG improves accuracy")));
        KnowledgeSearchTool tool = new KnowledgeSearchTool(retrievalService);
        assertThat(tool.searchKnowledge("test")).contains("Spring AI", "RAG");
    }

    @Test
    void shouldReturnNoResultsMessage() {
        when(retrievalService.retrieve(anyString())).thenReturn(List.of());
        assertThat(new KnowledgeSearchTool(retrievalService).searchKnowledge("x"))
                .containsIgnoringCase("no relevant");
    }
}
