package com.j2ee.tour.security;

import tools.jackson.databind.ObjectMapper;
import com.j2ee.tour.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final FixedWindowRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final boolean trustProxy;

    public RateLimitFilter(FixedWindowRateLimiter rateLimiter,
                           ObjectMapper objectMapper,
                           @Value("${app.security.trust-proxy:false}") boolean trustProxy) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.trustProxy = trustProxy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String bucket = "POST".equals(request.getMethod())
                ? bucketFor(request.getRequestURI())
                : null;
        if (bucket != null) {
            int limit = "refresh".equals(bucket) ? 30 : 10;
            long retryAfter = rateLimiter.acquire(clientIp(request) + "|" + bucket, limit);
            if (retryAfter > 0) {
                response.setStatus(429);
                response.setHeader("Retry-After", Long.toString(retryAfter));
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                objectMapper.writeValue(response.getWriter(),
                        ApiResponse.failure("Too many requests"));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String bucketFor(String path) {
        return switch (path) {
            case "/api/auth/login", "/api/auth/register" -> "credentials";
            case "/api/auth/refresh" -> "refresh";
            default -> null;
        };
    }

    private String clientIp(HttpServletRequest request) {
        if (!trustProxy) {
            return request.getRemoteAddr();
        }
        String header = request.getHeader("X-Forwarded-For");
        if (header == null || header.isBlank()) {
            return request.getRemoteAddr();
        }
        // Single trusted proxy: the rightmost hop is the one our proxy appended.
        String[] hops = header.split(",");
        return hops[hops.length - 1].trim();
    }
}
