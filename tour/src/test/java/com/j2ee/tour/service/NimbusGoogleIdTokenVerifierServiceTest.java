package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NimbusGoogleIdTokenVerifierServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC);
    private static final String CLIENT_ID = "test-client.apps.googleusercontent.com";
    private static final String ISSUER = "https://accounts.google.com";

    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;
    private NimbusGoogleIdTokenVerifierService verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        publicKey = (RSAPublicKey) pair.getPublic();
        privateKey = (RSAPrivateKey) pair.getPrivate();

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(CLOCK);
        decoder.setJwtValidator(timestamps);
        verifier = new NimbusGoogleIdTokenVerifierService(decoder, CLIENT_ID, CLOCK);
    }

    @Test
    void acceptsAValidGoogleToken() throws Exception {
        String token = token(ISSUER, List.of(CLIENT_ID), "google-sub", "person@example.com",
                Boolean.TRUE, Instant.now(CLOCK).plusSeconds(300));

        GoogleIdTokenVerifierService.GoogleIdentity identity = verifier.verify(token);

        assertThat(identity.subject()).isEqualTo("google-sub");
        assertThat(identity.email()).isEqualTo("person@example.com");
        assertThat(identity.emailVerified()).isTrue();
    }

    @Test
    void acceptsBothGoogleIssuerForms() throws Exception {
        String bareIssuer = token("accounts.google.com", List.of(CLIENT_ID), "google-sub",
                "person@example.com", Boolean.TRUE, Instant.now(CLOCK).plusSeconds(300));

        assertThat(verifier.verify(bareIssuer).subject()).isEqualTo("google-sub");
    }

    @Test
    void rejectsAWrongAudience() throws Exception {
        String token = token(ISSUER, List.of("another-client.apps.googleusercontent.com"),
                "google-sub", "person@example.com", Boolean.TRUE,
                Instant.now(CLOCK).plusSeconds(300));

        assertUnauthorized(() -> verifier.verify(token));
    }

    @Test
    void rejectsAWrongIssuer() throws Exception {
        String token = token("https://evil.example.com", List.of(CLIENT_ID), "google-sub",
                "person@example.com", Boolean.TRUE, Instant.now(CLOCK).plusSeconds(300));

        assertUnauthorized(() -> verifier.verify(token));
    }

    @Test
    void rejectsAnExpiredToken() throws Exception {
        String token = token(ISSUER, List.of(CLIENT_ID), "google-sub", "person@example.com",
                Boolean.TRUE, Instant.now(CLOCK).minusSeconds(30));

        assertUnauthorized(() -> verifier.verify(token));
    }

    @Test
    void rejectsAnUnverifiedEmail() throws Exception {
        String token = token(ISSUER, List.of(CLIENT_ID), "google-sub", "person@example.com",
                Boolean.FALSE, Instant.now(CLOCK).plusSeconds(300));

        assertUnauthorized(() -> verifier.verify(token));
    }

    @Test
    void rejectsAMissingEmail() throws Exception {
        String token = token(ISSUER, List.of(CLIENT_ID), "google-sub", null,
                Boolean.TRUE, Instant.now(CLOCK).plusSeconds(300));

        assertUnauthorized(() -> verifier.verify(token));
    }

    @Test
    void failsClosedWhenTheClientIdIsBlank() throws Exception {
        String token = token(ISSUER, List.of(CLIENT_ID), "google-sub", "person@example.com",
                Boolean.TRUE, Instant.now(CLOCK).plusSeconds(300));
        JwtDecoder neverCalled = ignored -> {
            throw new AssertionError("A blank client id must fail before decoding");
        };
        NimbusGoogleIdTokenVerifierService unconfigured =
                new NimbusGoogleIdTokenVerifierService(neverCalled, "  ", CLOCK);

        assertThatThrownBy(() -> unconfigured.verify(token))
                .isInstanceOf(ApiException.class)
                .hasMessage("Google sign-in is not configured")
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private String token(String issuer,
                         List<String> audience,
                         String subject,
                         String email,
                         Boolean emailVerified,
                         Instant expiresAt) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .audience(audience)
                .issueTime(Date.from(expiresAt.minusSeconds(600)))
                .expirationTime(Date.from(expiresAt));
        if (email != null) {
            claims.claim("email", email);
        }
        if (emailVerified != null) {
            claims.claim("email_verified", emailVerified);
        }

        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(jwk));
        return jwt.serialize();
    }

    private void assertUnauthorized(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
