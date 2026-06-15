package com.knowledge.assistant.chat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * GLM (ZhiPuAI) ChatClient - intended for Agent / tool-calling use (P0-3).
 *
 * Gated on spring.ai.zhipuai.api-key being non-empty via @ConditionalOnExpression.
 * NOTE: @ConditionalOnBean(ZhiPuAiChatModel.class) does NOT work here — it evaluates at
 * bean-definition registration, BEFORE the ZhiPuAI autoconfig (which runs after user
 * @Configuration classes) registers ZhiPuAiChatModel, so the condition never matched and
 * glmChatClient was silently never created. The property is readable from the Environment
 * at config-processing time, independent of bean ordering.
 */
@Configuration
@ConditionalOnExpression("'${spring.ai.zhipuai.api-key:}' != ''")
public class GlmChatClientConfig {

    @Bean
    public ChatClient glmChatClient(ZhiPuAiChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultSystem("你是一个知识库助手。根据提供的上下文文档回答用户问题。如果上下文中没有相关信息，请诚实回答不知道。回答使用中文。")
                .build();
    }
}
