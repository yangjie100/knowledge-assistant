package com.knowledge.assistant.agent.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextAnswerStepTest {

    @Mock private ChatClient chatClient;

    @Test
    void callsChatClientWithUserInput() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("AI answer");

        ContextAnswerStep step = new ContextAnswerStep(chatClient, "rag-answer");
        String result = step.execute("Context: ...\n\nQuestion: test?");

        assertThat(result).isEqualTo("AI answer");
        assertThat(step.name()).isEqualTo("rag-answer");
        verify(requestSpec).system(anyString());
    }

    @Test
    void usesCustomSystemPrompt() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("Custom answer");

        ContextAnswerStep step = new ContextAnswerStep(chatClient, "custom", "Custom prompt");
        step.execute("test");

        verify(requestSpec).system("Custom prompt");
    }
}
