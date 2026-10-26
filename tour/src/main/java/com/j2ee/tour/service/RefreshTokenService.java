package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.RefreshToken;
import com.j2ee.tour.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final short ACTIVE = 1;

    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository repository,
                               Clock clock,
                               @Value("${app.auth.refresh-token-ttl:P7D}") Duration ttl) {
        this.repository = repository;
        this.clock = clock;
        this.ttl = ttl;
    }

    public String issue(Account account) {
        String rawToken = randomToken();
        LocalDateTime now = LocalDateTime.now(clock);
        RefreshToken token = new RefreshToken();
        token.setAccount(account);
        token.setTokenHash(hash(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(ttl));
        token.setRevoked(false);
        repository.save(token);
        return rawToken;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw unauthorized();
        }
        RefreshToken current = repository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(this::unauthorized);

        if (current.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            throw unauthorized();
        }
        if (current.isRevoked()) {
            repository.revokeAllByAccountId(current.getAccount().getAccountId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected");
        }
        if (current.getAccount().getStatus() == null
                || current.getAccount().getStatus() != ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is disabled");
        }

        current.setRevoked(true);
        return new Rotation(current.getAccount(), issue(current.getAccount()));
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHashForUpdate(hash(rawToken))
                .ifPresent(token -> token.setRevoked(true));
    }

    public static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
    }

    public record Rotation(Account account, String refreshToken) {
    }
}
