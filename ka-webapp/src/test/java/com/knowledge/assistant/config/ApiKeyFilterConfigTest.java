package com.knowledge.assistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H-1: the filter registration itself must be conditional — no KA_API_KEY = no
 * filter bean = zero regression for local dev. Guards the @ConditionalOnExpression
 * string (a typo there fails silently).
 */
class ApiKeyFilterConfigTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(ApiKeyFilterConfig.class);

    @Test
    void filterNotRegisteredWithoutKey() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean("apiKeyFilterRegistration"));
    }

    @Test
    void filterRegisteredWithKeySet() {
        runner.withPropertyValues("ka.security.api-key=test-secret")
                .run(ctx -> assertThat(ctx).hasBean("apiKeyFilterRegistration"));
    }
}
