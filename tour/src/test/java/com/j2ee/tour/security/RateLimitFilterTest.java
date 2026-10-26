package com.j2ee.tour.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-26T00:00:30Z"), ZoneOffset.UTC);

    @Test
    void returns429WithRetryAfterOnceTheLimitIsExceeded() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(
                new FixedWindowRateLimiter(CLOCK), new ObjectMapper(), false);
        AtomicInteger chainCalls = new AtomicInteger();

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(loginRequest("1.2.3.4"), response,
                    (request, servletResponse) -> chainCalls.incrementAndGet());
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(loginRequest("1.2.3.4"), blocked,
                (request, servletResponse) -> chainCalls.incrementAndGet());

        assertThat(chainCalls).hasValue(10);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotBlank();
        assertThat(blocked.getContentAsString()).contains("Too many requests");
    }

    @Test
    void loginAndRegisterShareTheCredentialsBucket() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(
                new FixedWindowRateLimiter(CLOCK), new ObjectMapper(), false);

        for (int i = 0; i < 10; i++) {
            filter.doFilter(loginRequest("1.2.3.4"), new MockHttpServletResponse(),
                    (request, servletResponse) -> {
                    });
        }

        MockHttpServletRequest register = new MockHttpServletRequest("POST", "/api/auth/register");
        register.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(register, blocked, (request, servletResponse) -> {
        });

        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    @Test
    void doesNotLimitPublicGets() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(
                new FixedWindowRateLimiter(CLOCK), new ObjectMapper(), false);
        AtomicInteger chainCalls = new AtomicInteger();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tours");
        request.setRemoteAddr("1.2.3.4");

        for (int i = 0; i < 50; i++) {
            filter.doFilter(request, new MockHttpServletResponse(),
                    (servletRequest, servletResponse) -> chainCalls.incrementAndGet());
        }

        assertThat(chainCalls).hasValue(50);
    }

    @Test
    void usesTheRightmostForwardedHopWhenProxyIsTrusted() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(
                new FixedWindowRateLimiter(CLOCK), new ObjectMapper(), true);

        for (int i = 0; i < 10; i++) {
            filter.doFilter(forwardedLoginRequest("client-spoof, 10.0.0.1"),
                    new MockHttpServletResponse(),
                    (request, servletResponse) -> {
                    });
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(forwardedLoginRequest("client-spoof, 10.0.0.1"), blocked,
                (request, servletResponse) -> {
                });
        assertThat(blocked.getStatus()).isEqualTo(429);

        MockHttpServletResponse otherClient = new MockHttpServletResponse();
        filter.doFilter(forwardedLoginRequest("client-spoof, 10.0.0.2"), otherClient,
                (request, servletResponse) -> {
                });
        assertThat(otherClient.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest loginRequest(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private MockHttpServletRequest forwardedLoginRequest(String forwardedFor) {
        MockHttpServletRequest request = loginRequest("127.0.0.1");
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
