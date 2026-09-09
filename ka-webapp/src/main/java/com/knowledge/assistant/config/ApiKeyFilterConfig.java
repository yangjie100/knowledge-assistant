package com.knowledge.assistant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link ApiKeyFilter} for /api/* only when ka.security.api-key is set.
 * Empty key (local dev default) = no filter at all, zero regression.
 * Note: @ConditionalOnExpression (not @ConditionalOnBean) — user @Configuration
 * conditions are evaluated before autoconfig, see CLAUDE.md pitfall #3.
 */
@Configuration
@ConditionalOnExpression("'${ka.security.api-key:}' != ''")
public class ApiKeyFilterConfig {

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilterRegistration(
            @Value("${ka.security.api-key}") String apiKey) {
        FilterRegistrationBean<ApiKeyFilter> registration =
                new FilterRegistrationBean<>(new ApiKeyFilter(apiKey));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("apiKeyFilter");
        return registration;
    }
}
