package com.knowledge.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: full app context loads with or without ZHIPU_API_KEY.
 * Verifies spring.ai.model.embedding=ollama resolves the EmbeddingModel ambiguity
 * (Spring AI issue #1226) so the app actually boots after adding the zhipuai starter.
 */
@SpringBootTest
class ApplicationContextSmokeTest {

    @Autowired
    ApplicationContext ctx;

    @Test
    void contextLoadsAndSingleOllamaEmbeddingModel() {
        // With spring.ai.model.embedding=ollama, only ollamaEmbeddingModel exists.
        assertThat(ctx.getBeansOfType(EmbeddingModel.class)).hasSize(1);
        assertThat(ctx.getBean(OllamaChatModel.class)).isNotNull();
    }
}
