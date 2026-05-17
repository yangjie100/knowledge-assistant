package com.knowledge.assistant.chat.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "chat:memory:";
    private static final String CONVERSATIONS_KEY = "chat:conversations";
    private static final long TTL_HOURS = 24;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> findConversationIds() {
        return new ArrayList<>(Objects.requireNonNull(
                redisTemplate.opsForSet().members(CONVERSATIONS_KEY)));
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
        if (jsonList == null || jsonList.isEmpty()) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>();
        for (String json : jsonList) {
            deserializeMessage(json).ifPresent(messages::add);
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String key = KEY_PREFIX + conversationId;
        redisTemplate.delete(key);
        for (Message message : messages) {
            serializeMessage(message).ifPresent(json ->
                    redisTemplate.opsForList().rightPush(key, json));
        }
        redisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
        redisTemplate.opsForSet().add(CONVERSATIONS_KEY, conversationId);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
        redisTemplate.opsForSet().remove(CONVERSATIONS_KEY, conversationId);
    }

    private Optional<String> serializeMessage(Message message) {
        try {
            Map<String, String> map = new HashMap<>();
            map.put("type", message.getMessageType().getValue());
            map.put("content", message.getText());
            return Optional.of(objectMapper.writeValueAsString(map));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message", e);
            return Optional.empty();
        }
    }

    private Optional<Message> deserializeMessage(String json) {
        try {
            Map<String, String> map = objectMapper.readValue(json,
                    new TypeReference<>() {});
            String type = map.get("type");
            String content = map.get("content");
            return switch (type) {
                case "user" -> Optional.of(new UserMessage(content));
                case "assistant" -> Optional.of(new AssistantMessage(content));
                case "system" -> Optional.of(new SystemMessage(content));
                default -> {
                    log.warn("Unknown message type: {}", type);
                    yield Optional.empty();
                }
            };
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize message", e);
            return Optional.empty();
        }
    }
}
