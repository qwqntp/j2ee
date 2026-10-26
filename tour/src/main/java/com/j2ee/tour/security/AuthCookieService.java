package com.j2ee.tour.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieService {

    public static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/auth";

    private final boolean secure;
    private final Duration ttl;

    public AuthCookieService(@Value("${app.auth.cookie-secure:false}") boolean secure,
                             @Value("${app.auth.refresh-token-ttl:P7D}") Duration ttl) {
        this.secure = secure;
        this.ttl = ttl;
    }

    public ResponseCookie create(String refreshToken) {
        return base(refreshToken).maxAge(ttl).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(COOKIE_PATH);
    }
}
