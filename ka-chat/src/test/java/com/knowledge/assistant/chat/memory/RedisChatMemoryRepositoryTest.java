package com.knowledge.assistant.chat.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.assistant.chat.dto.ConversationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisChatMemoryRepositoryTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ListOperations<String, String> listOps;
    @Mock private SetOperations<String, String> setOps;
    @Mock private ValueOperations<String, String> valueOps;

    private RedisChatMemoryRepository repository;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        repository = new RedisChatMemoryRepository(redisTemplate, mapper);
    }

    @Test
    void findAllConversationInfoReturnsEmptyWhenNoIds() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("chat:conversations")).thenReturn(Set.of());

        List<ConversationInfo> result = repository.findAllConversationInfo();
        assertTrue(result.isEmpty());
    }

    @Test
    void deleteByConversationIdCleansMetaKey() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        repository.deleteByConversationId("conv-1");
        verify(redisTemplate).delete("chat:memory:conv-1");
        verify(redisTemplate).delete("chat:conv:meta:conv-1");
        verify(setOps).remove("chat:conversations", "conv-1");
    }

    @Test
    void saveAllUpdatesMetaAndStoresMessages() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        repository.saveAll("conv-1", List.of(
                new org.springframework.ai.chat.messages.UserMessage("Hello"),
                new org.springframework.ai.chat.messages.AssistantMessage("Hi there")
        ));

        verify(redisTemplate).delete("chat:memory:conv-1");
        verify(listOps, times(2)).rightPush(eq("chat:memory:conv-1"), anyString());
        verify(valueOps).set(eq("chat:conv:meta:conv-1"), anyString(), eq(Duration.ofHours(24)));
        verify(setOps).add("chat:conversations", "conv-1");
    }
}
