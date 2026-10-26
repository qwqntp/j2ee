package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.model.RefreshToken;
import com.j2ee.tour.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-26T00:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 7, 0);

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, CLOCK, Duration.ofDays(7));
    }

    @Test
    void issuesOpaqueTokenAndStoresOnlyItsHash() {
        Account account = account();
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = service.issue(account);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertThat(rawToken).isNotBlank();
        assertThat(saved.getTokenHash()).hasSize(64);
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.hash(rawToken));
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusDays(7));
        assertThat(saved.isRevoked()).isFalse();
    }

    @Test
    void rotatesAValidToken() {
        Account account = account();
        RefreshToken current = token(account, false, NOW.plusDays(1));
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("old")))
                .thenReturn(Optional.of(current));
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.Rotation rotation = service.rotate("old");

        assertThat(current.isRevoked()).isTrue();
        assertThat(rotation.account()).isSameAs(account);
        assertThat(rotation.refreshToken()).isNotBlank();
        assertThat(rotation.refreshToken()).isNotEqualTo("old");
    }

    @Test
    void reuseRevokesEveryActiveTokenForTheAccount() {
        Account account = account();
        RefreshToken current = token(account, true, NOW.plusDays(1));
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("reused")))
                .thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.rotate("reused"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(repository).revokeAllByAccountId(1L);
        verify(repository, never()).save(any(RefreshToken.class));
    }

    @Test
    void rejectsDisabledAccount() {
        Account account = account();
        account.setStatus((short) 0);
        RefreshToken current = token(account, false, NOW.plusDays(1));
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("disabled")))
                .thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.rotate("disabled"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(repository, never()).save(any(RefreshToken.class));
    }

    @Test
    void rejectsExpiredToken() {
        RefreshToken current = token(account(), false, NOW.minusSeconds(1));
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("expired")))
                .thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.rotate("expired"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsUnknownToken() {
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("unknown")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("unknown"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void logoutRevokesPresentedToken() {
        RefreshToken current = token(account(), false, NOW.plusDays(1));
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("current")))
                .thenReturn(Optional.of(current));

        service.revoke("current");

        assertThat(current.isRevoked()).isTrue();
    }

    private RefreshToken token(Account account, boolean revoked, LocalDateTime expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setId(1L);
        token.setAccount(account);
        token.setTokenHash("hash");
        token.setExpiresAt(expiresAt);
        token.setRevoked(revoked);
        token.setCreatedAt(NOW.minusDays(1));
        return token;
    }

    private Account account() {
        AccountRole role = new AccountRole();
        role.setAccountRoleId(2L);
        role.setRoleName("ROLE_USER");

        Account account = new Account();
        account.setAccountId(1L);
        account.setUsername("user01");
        account.setPassword("$2a$10$hash");
        account.setAccountEmail("user01@example.com");
        account.setEmailVerified((short) 0);
        account.setStatus((short) 1);
        account.setAccountCreatedAt(NOW.minusDays(10));
        account.setAccountUpdatedAt(NOW.minusDays(10));
        account.setRole(role);
        return account;
    }
}
