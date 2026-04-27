package com.knowledge.assistant.chat.controller;

import com.knowledge.assistant.common.dto.Result;
import com.knowledge.assistant.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public Result<Map<String, String>> chat(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        String conversationId = request.getOrDefault("conversationId", UUID.randomUUID().toString());
        String answer = chatService.chat(question, conversationId);
        return Result.ok(Map.of("answer", answer, "conversationId", conversationId));
    }
}
