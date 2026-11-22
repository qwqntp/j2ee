package com.j2ee.tour.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Clock;
import java.time.Duration;

/**
 * Decoder for Google ID tokens. Only the JWKS URI is configured here; signature
 * checking is lazy and no network call happens at startup.
 */
@Configuration
public class GoogleJwtConfig {

    static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    @Bean
    public JwtDecoder googleIdTokenDecoder(Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(timestamps);
        return decoder;
    }
}
