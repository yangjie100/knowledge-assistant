package com.knowledge.assistant.chat.advisor;

import com.knowledge.assistant.rag.service.RetrievalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagAdvisorTest {

    // DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER in Spring AI 1.1.4 (verified via javap -v).
    private static final int MEMORY_ADVISOR_ORDER = -2147482648;

    @Mock
    private RetrievalService retrievalService;

    @Test
    void beforeAugmentsUserMessageWithContextAndQuestion() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("docId", "d1");
        meta.put("rrfScore", 0.95);
        when(retrievalService.retrieve("What is Spring AI?"))
                .thenReturn(List.of(new Document("id1", "Spring AI is a framework.", meta)));

        RagAdvisor advisor = new RagAdvisor(retrievalService);
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("What is Spring AI?")))
                .build();

        // chain is unused by before(); null is safe.
        ChatClientRequest result = advisor.before(request, null);

        String text = result.prompt().getUserMessage().getText();
        assertThat(text).contains("Context:");
        assertThat(text).contains("[Source: d1 (score: 0.9500)]");
        assertThat(text).contains("Spring AI is a framework.");
        assertThat(text).contains("Question: What is Spring AI?");
        assertThat(text).endsWith("Please cite sources in your answer.");
    }

    @Test
    void beforePreservesQuestionWhenRetrievalReturnsEmpty() {
        when(retrievalService.retrieve(anyString())).thenReturn(List.of());

        RagAdvisor advisor = new RagAdvisor(retrievalService);
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("obscure query")))
                .build();

        ChatClientRequest result = advisor.before(request, null);
        String text = result.prompt().getUserMessage().getText();

        // Empty retrieval yields a blank context block; the question must still reach the model.
        assertThat(text).contains("Question: obscure query");
        assertThat(text).endsWith("Please cite sources in your answer.");
    }

    @Test
    void beforeJoinsMultipleDocumentsWithContext() {
        Map<String, Object> m1 = new HashMap<>();
        m1.put("docId", "d1");
        m1.put("rrfScore", 0.8);
        Map<String, Object> m2 = new HashMap<>();
        m2.put("docId", "d2");
        m2.put("rrfScore", 0.5);
        when(retrievalService.retrieve(anyString())).thenReturn(List.of(
                new Document("id1", "first chunk", m1),
                new Document("id2", "second chunk", m2)));

        RagAdvisor advisor = new RagAdvisor(retrievalService);
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("multi")))
                .build();

        String text = advisor.before(request, null).prompt().getUserMessage().getText();

        assertThat(text).contains("[Source: d1 (score: 0.8000)]", "[Source: d2 (score: 0.5000)]");
        assertThat(text).contains("first chunk", "second chunk");
    }

    @Test
    void getOrderRunsBeforeChatMemoryAdvisor() {
        RagAdvisor advisor = new RagAdvisor(retrievalService);
        // RagAdvisor must precede the memory advisor so memory captures the augmented message,
        // matching the prior manual-RAG behavior.
        assertThat(advisor.getOrder()).isLessThan(MEMORY_ADVISOR_ORDER);
    }

    @Test
    void afterIsPassthrough() {
        RagAdvisor advisor = new RagAdvisor(retrievalService);
        org.springframework.ai.chat.client.ChatClientResponse resp =
                org.springframework.ai.chat.client.ChatClientResponse.builder()
                        .chatResponse(new org.springframework.ai.chat.model.ChatResponse(List.of()))
                        .build();
        assertThat(advisor.after(resp, null)).isSameAs(resp);
    }
}
