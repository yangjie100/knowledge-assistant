package com.knowledge.assistant.agent.workflow;

import com.knowledge.assistant.rag.service.RetrievalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeSearchStepTest {

    @Mock private RetrievalService retrievalService;

    @Test
    void returnsContextPlusQuestionWhenDocsFound() {
        when(retrievalService.retrieve(anyString())).thenReturn(List.of(
                new Document("Spring AI is great"),
                new Document("RAG improves accuracy")
        ));
        KnowledgeSearchStep step = new KnowledgeSearchStep(retrievalService, "search");
        String result = step.execute("What is Spring AI?");

        assertThat(result).contains("Retrieved context:");
        assertThat(result).contains("Spring AI is great");
        assertThat(result).contains("Question: What is Spring AI?");
        assertThat(step.name()).isEqualTo("search");
    }

    @Test
    void returnsNoDocsMessageWhenEmpty() {
        when(retrievalService.retrieve(anyString())).thenReturn(List.of());
        KnowledgeSearchStep step = new KnowledgeSearchStep(retrievalService, "search");
        String result = step.execute("unknown topic");

        assertThat(result).contains("[No relevant documents found]");
        assertThat(result).contains("Question: unknown topic");
    }
}
