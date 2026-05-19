package com.knowledge.assistant.chat.controller;

import com.knowledge.assistant.chat.dto.ConversationInfo;
import com.knowledge.assistant.chat.service.ChatService;
import com.knowledge.assistant.common.dto.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock private ChatService chatService;
    @InjectMocks private ChatController controller;

    @Test
    void listConversationsReturnsAll() {
        List<ConversationInfo> conversations = List.of(
                new ConversationInfo("conv-1", LocalDateTime.now(), LocalDateTime.now(), 5, "test question")
        );
        when(chatService.listConversations()).thenReturn(conversations);

        Result<List<ConversationInfo>> result = controller.listConversations();

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().size());
        assertEquals("conv-1", result.getData().get(0).conversationId());
    }

    @Test
    void deleteConversationCallsService() {
        Result<Void> result = controller.deleteConversation("conv-1");

        assertTrue(result.isSuccess());
        verify(chatService).deleteConversation("conv-1");
    }
}
