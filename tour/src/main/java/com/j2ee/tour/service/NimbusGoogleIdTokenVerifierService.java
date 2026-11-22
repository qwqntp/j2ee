package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Verifies Google ID tokens with Spring Security's Nimbus decoder: the signature is
 * checked against Google's JWKS, and this class additionally enforces both issuer
 * spellings, the configured audience, expiry (with the injected Clock) and
 * {@code email_verified}.
 */
@Service
public class NimbusGoogleIdTokenVerifierService implements GoogleIdTokenVerifierService {

    static final Set<String> GOOGLE_ISSUERS =
            Set.of("accounts.google.com", "https://accounts.google.com");

    private final JwtDecoder decoder;
    private final String clientId;
    private final Clock clock;

    public NimbusGoogleIdTokenVerifierService(
            JwtDecoder decoder,
            @Value("${app.google.client-id:}") String clientId,
            Clock clock) {
        this.decoder = decoder;
        this.clientId = clientId;
        this.clock = clock;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (clientId == null || clientId.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google sign-in is not configured");
        }
        if (idToken == null || idToken.isBlank()) {
            throw invalidToken();
        }

        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidToken();
        }

        String issuer = jwt.getClaimAsString("iss");
        if (!GOOGLE_ISSUERS.contains(issuer)) {
            throw invalidToken();
        }

        List<String> audience = jwt.getAudience();
        if (audience == null || !audience.contains(clientId)) {
            throw invalidToken();
        }

        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(clock.instant())) {
            throw invalidToken();
        }

        if (!isTrue(jwt.getClaims().get("email_verified"))) {
            throw invalidToken();
        }

        String email = jwt.getClaimAsString("email");
        String subject = jwt.getSubject();
        if (email == null || email.isBlank() || subject == null || subject.isBlank()) {
            throw invalidToken();
        }

        return new GoogleIdentity(subject, email.trim(), true);
    }

    private boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value)
                || (value != null && "true".equalsIgnoreCase(String.valueOf(value)));
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid Google ID token");
    }
}
