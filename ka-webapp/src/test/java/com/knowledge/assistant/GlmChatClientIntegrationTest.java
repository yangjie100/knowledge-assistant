package com.knowledge.assistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0-2 end-to-end verification: multi-ChatModel coexistence + real GLM-5.2 call.
 * Gated by @EnabledIfEnvironmentVariable so it doubles as a regression test:
 * no ZHIPU_API_KEY -> entire class skipped (never fails); key present -> verifies.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
class GlmChatClientIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(GlmChatClientIntegrationTest.class);

    @Autowired
    ApplicationContext ctx;

    @Test
    void multipleChatModelsCoexistWithoutConflict() {
        // Core P0-2 assertion: Spring AI issue #1226 (NoUniqueBeanDefinition when two
        // chat starters on classpath) is resolved by injecting concrete ChatModel types.
        assertThat(ctx.getBean(OllamaChatModel.class)).isNotNull();
        assertThat(ctx.getBean(ZhiPuAiChatModel.class)).isNotNull();
    }

    @Test
    void bothNamedChatClientsPresentAndDistinct() {
        ChatClient ollamaClient = ctx.getBean("chatClient", ChatClient.class);
        ChatClient glmClient = ctx.getBean("glmChatClient", ChatClient.class);
        assertThat(ollamaClient).isNotSameAs(glmClient);
    }

    @Test
    void glmChatClientActuallyInvokesGlmApi() {
        ChatClient glmClient = ctx.getBean("glmChatClient", ChatClient.class);
        String resp = glmClient.prompt()
                .user("用一句话介绍你自己，不超过30字。")
                .call()
                .content();
        assertThat(resp).isNotBlank();
        log.info("[GLM-5.2 实测响应] {}", resp);
    }
}
