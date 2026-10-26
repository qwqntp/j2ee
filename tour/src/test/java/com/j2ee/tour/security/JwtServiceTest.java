package com.j2ee.tour.security;

import com.j2ee.tour.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "01234567890123456789012345678901";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");

    @Test
    void issuesAndParsesAccessToken() {
        JwtService jwtService = serviceAt(NOW);

        String token = jwtService.issue(7L, "user01", "ROLE_USER");
        AuthPrincipal principal = jwtService.parse(token);

        assertThat(principal.id()).isEqualTo(7L);
        assertThat(principal.username()).isEqualTo("user01");
        assertThat(principal.role()).isEqualTo("ROLE_USER");
    }

    @Test
    void rejectsExpiredToken() {
        String token = serviceAt(NOW).issue(7L, "user01", "ROLE_USER");
        JwtService later = serviceAt(NOW.plus(Duration.ofMinutes(16)));

        assertThatThrownBy(() -> later.parse(token))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void rejectsTamperedToken() {
        String token = serviceAt(NOW).issue(7L, "user01", "ROLE_USER");

        assertThatThrownBy(() -> serviceAt(NOW).parse(token + "x"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtService("short-secret", Duration.ofMinutes(15), Clock.fixed(NOW, ZONE)))
                .isInstanceOf(IllegalStateException.class);
    }

    private JwtService serviceAt(Instant instant) {
        return new JwtService(SECRET, Duration.ofMinutes(15), Clock.fixed(instant, ZONE));
    }
}
