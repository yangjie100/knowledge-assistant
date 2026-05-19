package com.knowledge.assistant.chat.controller;

import com.knowledge.assistant.chat.dto.ChatRequest;
import com.knowledge.assistant.chat.dto.ChatResponse;
import com.knowledge.assistant.chat.dto.ConversationInfo;
import com.knowledge.assistant.common.dto.Result;
import com.knowledge.assistant.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        String conversationId = request.conversationId() != null && !request.conversationId().isBlank()
                ? request.conversationId()
                : UUID.randomUUID().toString();
        String answer = chatService.chat(request.question(), conversationId);
        return Result.ok(new ChatResponse(answer, conversationId));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @RequestParam String question,
            @RequestParam(required = false) String conversationId) {
        String convId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : UUID.randomUUID().toString();
        return chatService.streamChat(question, convId);
    }

    @GetMapping("/history/{conversationId}")
    public Result<List<Message>> getHistory(@PathVariable String conversationId) {
        return Result.ok(chatService.getHistory(conversationId));
    }

    @GetMapping("/conversations")
    public Result<List<ConversationInfo>> listConversations() {
        return Result.ok(chatService.listConversations());
    }

    @DeleteMapping("/conversations/{conversationId}")
    public Result<Void> deleteConversation(@PathVariable String conversationId) {
        chatService.deleteConversation(conversationId);
        return Result.ok(null);
    }
}
