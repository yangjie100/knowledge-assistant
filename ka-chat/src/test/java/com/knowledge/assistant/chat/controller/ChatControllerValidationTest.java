package com.knowledge.assistant.chat.controller;

import com.knowledge.assistant.chat.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M-2: bean validation on /api/chat must actually fire — @Valid on the @RequestBody
 * plus @Pattern on conversationId (Redis key injection guard). Standalone MockMvc so
 * no Spring context / Redis / Ollama is needed.
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerValidationTest {

    @Mock private ChatService chatService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService)).build();
    }

    @Test
    void chatRejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatRejectsOverlongQuestion() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatRejectsInjectionConversationId() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"hi\",\"conversationId\":\"a:b\\rc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatAcceptsMissingConversationId() throws Exception {
        when(chatService.chat(eq("hi"), anyString())).thenReturn("ok");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"hi\"}"))
                .andExpect(status().isOk());
    }
}
