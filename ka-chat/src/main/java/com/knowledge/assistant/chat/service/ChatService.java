package com.knowledge.assistant.chat.service;

import com.knowledge.assistant.chat.advisor.RagAdvisor;
import com.knowledge.assistant.chat.dto.ConversationInfo;
import com.knowledge.assistant.chat.memory.RedisChatMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final RagAdvisor ragAdvisor;
    private final ChatMemory chatMemory;
    private final RedisChatMemoryRepository chatMemoryRepository;

    public String chat(String question, String conversationId) {
        log.info("Chat question: {}, conversationId: {}", question, conversationId);
        return chatClient.prompt()
                .user(question)
                .advisors(a -> a.param("chat_memory_conversation_id", conversationId).advisors(ragAdvisor))
                .call()
                .content();
    }

    public Flux<String> streamChat(String question, String conversationId) {
        log.info("Stream chat question: {}, conversationId: {}", question, conversationId);
        return chatClient.prompt()
                .user(question)
                .advisors(a -> a.param("chat_memory_conversation_id", conversationId).advisors(ragAdvisor))
                .stream()
                .content();
    }

    public List<Message> getHistory(String conversationId) {
        log.info("Get history for conversationId: {}", conversationId);
        return chatMemory.get(conversationId);
    }

    public List<ConversationInfo> listConversations() {
        return chatMemoryRepository.findAllConversationInfo();
    }

    public void deleteConversation(String conversationId) {
        chatMemoryRepository.deleteByConversationId(conversationId);
    }
}
