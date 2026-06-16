package com.knowledge.assistant.chat.service;

import com.knowledge.assistant.chat.advisor.RagAdvisor;
import com.knowledge.assistant.chat.memory.RedisChatMemoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ChatClient chatClient;
    @Mock private RagAdvisor ragAdvisor;
    @Mock private ChatMemory chatMemory;
    @Mock private RedisChatMemoryRepository chatMemoryRepository;

    @Test
    void chatPassesRawQuestionAndRegistersRagAdvisor() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("Spring AI is a framework.");

        ChatService service = new ChatService(chatClient, ragAdvisor, chatMemory, chatMemoryRepository);
        String result = service.chat("What is Spring AI?", "conv-1");

        assertThat(result).isEqualTo("Spring AI is a framework.");
        // Raw question is passed verbatim; retrieval + context augmentation now lives in RagAdvisor.
        verify(requestSpec).user("What is Spring AI?");
        verify(requestSpec).advisors(any(Consumer.class));
    }

    @Test
    void streamChatPassesRawQuestionAndStreams() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(reactor.core.publisher.Flux.empty());

        ChatService service = new ChatService(chatClient, ragAdvisor, chatMemory, chatMemoryRepository);
        service.streamChat("stream query", "conv-2");

        verify(requestSpec).user("stream query");
        verify(requestSpec).stream();
    }
}
