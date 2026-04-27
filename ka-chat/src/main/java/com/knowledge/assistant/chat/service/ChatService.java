package com.knowledge.assistant.chat.service;

import com.knowledge.assistant.rag.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final RetrievalService retrievalService;

    public String chat(String question, String conversationId) {
        log.info("Chat question: {}, conversationId: {}", question, conversationId);

        List<Document> context = retrievalService.retrieve(question);
        String contextText = context.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        return chatClient.prompt()
                .user(userSpec -> userSpec
                        .text("Context:\n{context}\n\nQuestion: {question}")
                        .param("context", contextText)
                        .param("question", question))
                .advisors(a -> a.param("chat_memory_conversation_id", conversationId))
                .call()
                .content();
    }
}
