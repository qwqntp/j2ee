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
    void changePasswordHasItsOwnBucketWithTenPerMinuteLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(
                new FixedWindowRateLimiter(CLOCK), new ObjectMapper(), false);
        AtomicInteger chainCalls = new AtomicInteger();

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(changePasswordRequest("1.2.3.4"), response,
                    (request, servletResponse) -> chainCalls.incrementAndGet());
            assertThat(response.getStatus()).isEqualTo(200);
        }

        // The credentials bucket is untouched, so login from the same IP still passes.
        MockHttpServletResponse login = new MockHttpServletResponse();
        filter.doFilter(loginRequest("1.2.3.4"), login,
                (request, servletResponse) -> chainCalls.incrementAndGet());
        assertThat(login.getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(changePasswordRequest("1.2.3.4"), blocked,
                (request, servletResponse) -> chainCalls.incrementAndGet());

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotBlank();
        assertThat(chainCalls).hasValue(11);
    }

    @Test
    void forgotPasswordHasFivePerMinuteLimitAndRetryAfter() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(
                new FixedWindowRateLimiter(CLOCK), new ObjectMapper(), false);
        AtomicInteger allowed = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(authRequest("forgot-password"), response,
                    (request, servletResponse) -> allowed.incrementAndGet());
            assertThat(response.getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(authRequest("forgot-password"), blocked,
                (request, servletResponse) -> allowed.incrementAndGet());
        assertThat(allowed).hasValue(5);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("30");
    }

    @Test
    void resetPasswordHasTenPerMinuteLimitAndRetryAfter() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(
                new FixedWindowRateLimiter(CLOCK), new ObjectMapper(), false);
        AtomicInteger allowed = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(authRequest("reset-password"), response,
                    (request, servletResponse) -> allowed.incrementAndGet());
            assertThat(response.getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(authRequest("reset-password"), blocked,
                (request, servletResponse) -> allowed.incrementAndGet());
        assertThat(allowed).hasValue(10);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("30");
    }

    @Test
    void recoveryBucketsAreIndependentOfEachOtherAndExistingAuthBuckets() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(
                new FixedWindowRateLimiter(CLOCK), new ObjectMapper(), false);
        for (int i = 0; i < 10; i++) {
            filter.doFilter(loginRequest("1.2.3.4"), new MockHttpServletResponse(),
                    (request, response) -> { });
        }
        MockHttpServletResponse registerBlocked = new MockHttpServletResponse();
        filter.doFilter(authRequest("register"), registerBlocked, (request, response) -> { });
        assertThat(registerBlocked.getStatus()).isEqualTo(429);

        for (String path : new String[] {"change-password", "forgot-password", "reset-password"}) {
            MockHttpServletResponse independent = new MockHttpServletResponse();
            filter.doFilter(authRequest(path), independent, (request, response) -> { });
            assertThat(independent.getStatus()).as(path).isEqualTo(200);
        }
        for (int i = 0; i < 4; i++) {
            filter.doFilter(authRequest("forgot-password"), new MockHttpServletResponse(),
                    (request, response) -> { });
        }
        MockHttpServletResponse forgotBlocked = new MockHttpServletResponse();
        filter.doFilter(authRequest("forgot-password"), forgotBlocked, (request, response) -> { });
        assertThat(forgotBlocked.getStatus()).isEqualTo(429);

        for (String path : new String[] {"change-password", "reset-password"}) {
            MockHttpServletResponse independent = new MockHttpServletResponse();
            filter.doFilter(authRequest(path), independent, (request, response) -> { });
            assertThat(independent.getStatus()).as(path).isEqualTo(200);
        }
        MockHttpServletResponse otherIp = new MockHttpServletResponse();
        MockHttpServletRequest request = authRequest("forgot-password");
        request.setRemoteAddr("2.3.4.5");
        filter.doFilter(request, otherIp, (req, response) -> { });
        assertThat(otherIp.getStatus()).isEqualTo(200);
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

    private MockHttpServletRequest authRequest(String action) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/" + action);
        request.setRemoteAddr("1.2.3.4");
        return request;
    }

    private MockHttpServletRequest changePasswordRequest(String remoteAddress) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/auth/change-password");
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private MockHttpServletRequest forwardedLoginRequest(String forwardedFor) {
        MockHttpServletRequest request = loginRequest("127.0.0.1");
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
