package com.knowledge.assistant.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * H-1 (option A): API-key gate for /api/** — a single-user internal tool does not
 * need a full auth stack, but anonymous delete/upload/LLM-spend must be blocked
 * once exposed beyond loopback.
 *
 * Credentials are accepted via:
 * <ul>
 *   <li>{@code X-API-Key} header — regular fetch/axios calls;</li>
 *   <li>{@code ka_api_key} cookie — EventSource (SSE) cannot set custom headers,
 *       and this app's static chat UI is same-origin, so the cookie rides along.</li>
 * </ul>
 *
 * Registered by {@link ApiKeyFilterConfig} only when {@code ka.security.api-key}
 * is non-empty (inert-until-configured, same pattern as the other KA_* switches).
 * Loopback binding in docker-compose remains the outer safety net.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-API-Key";
    static final String COOKIE_NAME = "ka_api_key";

    private final byte[] configuredKey;

    public ApiKeyFilter(String apiKey) {
        this.configuredKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER_NAME);
        if (provided == null) {
            provided = extractCookie(request);
        }
        if (provided == null || !MessageDigest.isEqual(configuredKey,
                provided.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":401,\"success\":false,\"message\":\"Missing or invalid API key\",\"data\":null}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String extractCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return urlDecode(cookie.getValue());
            }
        }
        return null;
    }

    /**
     * chat-app.js writes the cookie via encodeURIComponent, so decode it back before
     * comparing (symmetric with the JS side). Malformed percent-sequences simply
     * fail to match — treated as no key, i.e. 401.
     */
    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
