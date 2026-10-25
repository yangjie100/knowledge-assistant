package com.knowledge.assistant.agent.config;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Global guardrail for all autoconfigured RestClient-based clients (Spring AI zhipuai /
 * ollama): a hung TCP connection or a stalled LLM stream must fail at the read timeout
 * instead of holding the calling thread forever.
 *
 * <p>Budget: local Ollama 27B generation takes ~3 minutes on this machine, so the read
 * timeout is 5 minutes with headroom — it guards against true hangs, not slow responses.
 *
 * <p>Scope note: only clients built from the autoconfigured RestClient.Builder are
 * affected. TeiEmbeddingModel and RerankClient build their own request factories with
 * explicit timeouts (60s / 10s) and keep those values.
 */
@Configuration
public class RestClientTimeoutConfig {

    @Bean
    public RestClientCustomizer restClientTimeoutCustomizer() {
        return builder -> builder.requestFactory(ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofSeconds(10))
                        .withReadTimeout(Duration.ofMinutes(5))));
    }
}
