package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AuthToken;
import com.j2ee.tour.repository.AuthTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthTokenServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 10, 0);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-07T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private AuthTokenRepository repository;
    private AuthTokenService service;
    private Account account;

    @BeforeEach
    void setUp() {
        repository = mock(AuthTokenRepository.class);
        service = new AuthTokenService(repository, CLOCK,
                Duration.ofMinutes(30), Duration.ofDays(2));
        account = new Account();
        account.setAccountId(1L);
        account.setUsername("user01");
        account.setAccountEmail("user01@example.com");
    }

    @Test
    void issueStoresHashAndExpiryNotRawToken() {
        when(repository.save(any(AuthToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String raw = service.issue(account, AuthTokenService.PASSWORD_RESET);

        ArgumentCaptor<AuthToken> captor = ArgumentCaptor.forClass(AuthToken.class);
        verify(repository).save(captor.capture());
        AuthToken saved = captor.getValue();
        assertNotEquals(raw, saved.getTokenHash());
        assertEquals(RefreshTokenService.hash(raw), saved.getTokenHash());
        assertEquals(NOW.plusMinutes(30), saved.getExpiresAt());
        assertEquals("PASSWORD_RESET", saved.getTokenType());
    }

    @Test
    void consumeMarksUnusedTokenAsUsed() {
        AuthToken token = token(NOW.plusMinutes(10), null);
        when(repository.findForUpdate(
                RefreshTokenService.hash("raw-token"), AuthTokenService.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(repository.save(token)).thenReturn(token);

        AuthToken consumed = service.consume("raw-token", AuthTokenService.PASSWORD_RESET);

        assertEquals(NOW, consumed.getUsedAt());
    }

    @Test
    void consumeRejectsReusedToken() {
        AuthToken token = token(NOW.plusMinutes(10), NOW.minusMinutes(1));
        when(repository.findForUpdate(
                RefreshTokenService.hash("raw-token"), AuthTokenService.PASSWORD_RESET))
                .thenReturn(Optional.of(token));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.consume("raw-token", AuthTokenService.PASSWORD_RESET));

        assertEquals("Invalid or expired token", exception.getMessage());
    }

    @Test
    void consumeRejectsExpiredToken() {
        AuthToken token = token(NOW.minusMinutes(1), null);
        when(repository.findForUpdate(
                RefreshTokenService.hash("raw-token"), AuthTokenService.PASSWORD_RESET))
                .thenReturn(Optional.of(token));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.consume("raw-token", AuthTokenService.PASSWORD_RESET));

        assertEquals("Invalid or expired token", exception.getMessage());
    }

    @Test
    void consumeRejectsUnknownToken() {
        when(repository.findForUpdate(
                RefreshTokenService.hash("missing"), AuthTokenService.PASSWORD_RESET))
                .thenReturn(Optional.empty());

        assertThrows(ApiException.class,
                () -> service.consume("missing", AuthTokenService.PASSWORD_RESET));
    }

    @Test
    void issueReplacesExistingTokensOfSameType() {
        when(repository.save(any(AuthToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.issue(account, AuthTokenService.EMAIL_VERIFICATION);

        verify(repository).deleteByAccountIdAndType(
                account.getAccountId(), AuthTokenService.EMAIL_VERIFICATION);
    }

    private AuthToken token(LocalDateTime expiresAt, LocalDateTime usedAt) {
        AuthToken token = new AuthToken();
        token.setAccount(account);
        token.setTokenType(AuthTokenService.PASSWORD_RESET);
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);
        token.setCreatedAt(NOW);
        return token;
    }
}
