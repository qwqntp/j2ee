package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AuthToken;
import com.j2ee.tour.repository.AuthTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class AuthTokenService {

    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthTokenRepository repository;
    private final Clock clock;
    private final Duration passwordResetTtl;
    private final Duration emailVerificationTtl;

    public AuthTokenService(AuthTokenRepository repository,
                            Clock clock,
                            @Value("${app.auth.password-reset-token-ttl:PT30M}")
                            Duration passwordResetTtl,
                            @Value("${app.auth.email-verification-token-ttl:P2D}")
                            Duration emailVerificationTtl) {
        this.repository = repository;
        this.clock = clock;
        this.passwordResetTtl = passwordResetTtl;
        this.emailVerificationTtl = emailVerificationTtl;
    }

    @Transactional
    public String issue(Account account, String tokenType) {
        String rawToken = randomToken();
        LocalDateTime now = LocalDateTime.now(clock);
        repository.deleteByAccountIdAndType(account.getAccountId(), tokenType);

        AuthToken token = new AuthToken();
        token.setAccount(account);
        token.setTokenHash(RefreshTokenService.hash(rawToken));
        token.setTokenType(tokenType);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(ttl(tokenType)));
        repository.save(token);
        return rawToken;
    }

    @Transactional
    public AuthToken consume(String rawToken, String tokenType) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        AuthToken token = repository.findForUpdate(
                        RefreshTokenService.hash(rawToken), tokenType)
                .orElseThrow(this::invalidToken);

        LocalDateTime now = LocalDateTime.now(clock);
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }
        token.setUsedAt(now);
        return repository.save(token);
    }

    private Duration ttl(String tokenType) {
        if (PASSWORD_RESET.equals(tokenType)) {
            return passwordResetTtl;
        }
        if (EMAIL_VERIFICATION.equals(tokenType)) {
            return emailVerificationTtl;
        }
        throw new IllegalArgumentException("Unsupported auth token type: " + tokenType);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
    }
}
