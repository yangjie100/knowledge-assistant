package com.knowledge.assistant.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaHealthIndicator implements HealthIndicator {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public Health health() {
        try {
            String baseUrl = System.getenv().getOrDefault("SPRING_AI_OLLAMA_BASE_URL", "http://localhost:11434");
            restTemplate.getForObject(baseUrl + "/api/tags", String.class);
            return Health.up().withDetail("url", baseUrl).build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}
