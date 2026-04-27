package com.knowledge.assistant.chat.controller;

import com.knowledge.assistant.chat.dto.ChatRequest;
import com.knowledge.assistant.chat.dto.ChatResponse;
import com.knowledge.assistant.common.dto.Result;
import com.knowledge.assistant.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public Result<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String conversationId = request.conversationId() != null && !request.conversationId().isBlank()
                ? request.conversationId()
                : UUID.randomUUID().toString();
        String answer = chatService.chat(request.question(), conversationId);
        return Result.ok(new ChatResponse(answer, conversationId));
    }
}
