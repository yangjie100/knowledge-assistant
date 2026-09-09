package com.knowledge.assistant.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H-1 (option A): X-API-Key gate for /api/**. The filter must reject missing and
 * wrong keys (constant-time compare), and accept the key via header (fetch) or
 * cookie (EventSource cannot set headers).
 */
class ApiKeyFilterTest {

    private static final String SECRET = "test-secret-key";

    private ApiKeyFilter filter;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyFilter(SECRET);
        response = new MockHttpServletResponse();
    }

    @Test
    void rejectsMissingKey() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/knowledge/list");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsWrongKey() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/knowledge/list");
        request.addHeader("X-API-Key", "wrong");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void acceptsValidKeyHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/knowledge/list");
        request.addHeader("X-API-Key", SECRET);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull(); // reached the downstream chain
    }

    @Test
    void acceptsValidKeyCookieForEventSource() throws ServletException, IOException {
        // EventSource cannot send custom headers — the cookie path covers the SSE endpoint.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chat/stream");
        request.setCookies(new jakarta.servlet.http.Cookie("ka_api_key", SECRET));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void rejectsWrongKeyCookie() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chat/stream");
        request.setCookies(new jakarta.servlet.http.Cookie("ka_api_key", "wrong"));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull(); // blocked before the chain
    }

    @Test
    void acceptsUrlEncodedKeyCookie() throws ServletException, IOException {
        // chat-app.js writes encodeURIComponent(key) into the cookie — keys containing
        // '+'/'/' must survive the round-trip, so the filter decodes before comparing.
        String key = "s3cr3t+key/256";
        ApiKeyFilter encodedFilter = new ApiKeyFilter(key);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chat/stream");
        request.setCookies(new jakarta.servlet.http.Cookie("ka_api_key",
                URLEncoder.encode(key, StandardCharsets.UTF_8)));
        MockFilterChain chain = new MockFilterChain();

        encodedFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void rejectsMalformedEncodedCookie() throws ServletException, IOException {
        // A malformed percent-sequence decodes to "no key" -> 401, never an exception.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chat/stream");
        request.setCookies(new jakarta.servlet.http.Cookie("ka_api_key", "bad%zz%"));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void unauthorizedBodyIsResultJson() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/knowledge/list");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getContentAsString())
                .contains("\"success\":false")
                .contains("401");
    }
}
