package com.knowledge.assistant.chat.service;

import com.knowledge.assistant.rag.service.RetrievalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private RetrievalService retrievalService;

    @Test
    void chatCallsRetrievalServiceWithQuestion() {
        when(retrievalService.retrieve(anyString())).thenReturn(List.of(
                new Document("Spring AI is a framework for AI applications.")
        ));

        // Mock ChatClient fluent API chain
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.advisors(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("Spring AI is a framework.");

        ChatService service = new ChatService(chatClient, retrievalService);
        String result = service.chat("What is Spring AI?", "conv-1");

        assertThat(result).isEqualTo("Spring AI is a framework.");
        verify(retrievalService).retrieve("What is Spring AI?");
    }

    @Test
    void chatWithEmptyContextStillCallsRetrievalService() {
        when(retrievalService.retrieve(anyString())).thenReturn(List.of());

        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.advisors(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("I don't know.");

        ChatService service = new ChatService(chatClient, retrievalService);
        String result = service.chat("unknown topic", "conv-2");

        assertThat(result).isEqualTo("I don't know.");
        verify(retrievalService).retrieve("unknown topic");
    }

    @Test
    void chatWithMultipleDocumentsJoinsContext() {
        when(retrievalService.retrieve(anyString())).thenReturn(List.of(
                new Document("Doc 1 content"),
                new Document("Doc 2 content")
        ));

        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.advisors(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("Combined answer.");

        ChatService service = new ChatService(chatClient, retrievalService);
        String result = service.chat("multi-doc query", "conv-3");

        assertThat(result).isEqualTo("Combined answer.");
        verify(retrievalService).retrieve("multi-doc query");
        verify(chatClient).prompt();
    }
}
