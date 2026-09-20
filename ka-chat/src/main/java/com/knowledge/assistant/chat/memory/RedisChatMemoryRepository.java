package com.knowledge.assistant.chat.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.assistant.chat.dto.ConversationInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "chat:memory:";
    private static final String CONVERSATIONS_KEY = "chat:conversations";
    private static final String META_KEY_PREFIX = "chat:conv:meta:";
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
        updateConversationMeta(conversationId, messages);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
        redisTemplate.opsForSet().remove(CONVERSATIONS_KEY, conversationId);
        redisTemplate.delete(META_KEY_PREFIX + conversationId);
    }

    public List<ConversationInfo> findAllConversationInfo() {
        Set<String> ids = redisTemplate.opsForSet().members(CONVERSATIONS_KEY);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<ConversationInfo> result = new ArrayList<>();
        for (String id : ids) {
            // Redis set members cannot expire individually: the message key dies at TTL 24h
            // but its entry stays in the index forever. Lazily purge such ghosts on read.
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + id))) {
                log.info("Purging ghost conversation from index: {}", id);
                redisTemplate.opsForSet().remove(CONVERSATIONS_KEY, id);
                continue;
            }
            readMeta(id).ifPresent(result::add);
        }
        result.sort(Comparator.comparing(
                (ConversationInfo c) -> c.lastMessageAt() != null ? c.lastMessageAt() : c.createdAt(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private void updateConversationMeta(String conversationId, List<Message> messages) {
        String metaKey = META_KEY_PREFIX + conversationId;
        String existing = redisTemplate.opsForValue().get(metaKey);

        LocalDateTime createdAt = LocalDateTime.now();
        if (existing != null) {
            try {
                Map<String, Object> map = objectMapper.readValue(existing, new TypeReference<>() {});
                if (map.get("createdAt") != null) {
                    createdAt = LocalDateTime.parse(map.get("createdAt").toString());
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse existing meta for {}", conversationId, e);
            }
        }

        String lastQuestion = messages.stream()
                .filter(m -> m instanceof UserMessage)
                .map(Message::getText)
                .reduce((first, second) -> second)
                .orElse(null);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("conversationId", conversationId);
        meta.put("createdAt", createdAt.toString());
        meta.put("lastMessageAt", LocalDateTime.now().toString());
        meta.put("messageCount", messages.size());
        meta.put("lastQuestion", lastQuestion);

        try {
            redisTemplate.opsForValue().set(metaKey, objectMapper.writeValueAsString(meta),
                    Duration.ofHours(TTL_HOURS));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize conversation meta", e);
        }
    }

    private Optional<ConversationInfo> readMeta(String conversationId) {
        String json = redisTemplate.opsForValue().get(META_KEY_PREFIX + conversationId);
        if (json == null) {
            return Optional.of(new ConversationInfo(conversationId, null, null, 0, null));
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            return Optional.of(new ConversationInfo(
                    conversationId,
                    map.get("createdAt") != null ? LocalDateTime.parse(map.get("createdAt").toString()) : null,
                    map.get("lastMessageAt") != null ? LocalDateTime.parse(map.get("lastMessageAt").toString()) : null,
                    map.get("messageCount") != null ? ((Number) map.get("messageCount")).intValue() : 0,
                    (String) map.get("lastQuestion")
            ));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize conversation meta for {}", conversationId, e);
            return Optional.of(new ConversationInfo(conversationId, null, null, 0, null));
        }
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
