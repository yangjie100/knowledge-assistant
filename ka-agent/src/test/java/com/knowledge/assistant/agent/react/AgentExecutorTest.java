package com.knowledge.assistant.agent.react;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AgentExecutorTest {

    @Mock
    ChatClient chatClient;

    private AgentExecutor agent;

    @BeforeEach
    void setUp() {
        agent = new AgentExecutor(chatClient);
    }

    @Test
    void shouldThrowOnNullQuestion() {
        assertThatThrownBy(() -> agent.execute(new AgentRequest(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnBlankQuestion() {
        assertThatThrownBy(() -> agent.execute(new AgentRequest("  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnEmptyQuestion() {
        assertThatThrownBy(() -> agent.execute(new AgentRequest("")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
