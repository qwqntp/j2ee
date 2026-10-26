package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.request.LoginRequest;
import com.j2ee.tour.dto.request.RegisterRequest;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.AccountRoleRepository;
import com.j2ee.tour.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-26T00:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountRoleRepository roleRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                accountRepository,
                roleRepository,
                refreshTokenService,
                passwordEncoder,
                jwtService,
                CLOCK);
    }

    @Test
    void registerRejectsPasswordOver72Utf8Bytes() {
        String password = "€".repeat(25);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(75);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("new-user", password, "new@example.com")))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(accountRepository, roleRepository, refreshTokenService);
    }

    @Test
    void registerRejectsDuplicateEmailCaseInsensitively() {
        when(accountRepository.existsByUsername("new-user")).thenReturn(false);
        when(accountRepository.existsByAccountEmailIgnoreCase("New@Example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("new-user", "password123", "New@Example.com")))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(accountRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loginRejectsPasswordOver72Utf8Bytes() {
        String password = "€".repeat(25);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user01", password)))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(accountRepository, passwordEncoder);
    }

    @Test
    void loginRejectsUnknownUsernameWithDummyHashCheck() {
        when(accountRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("password123"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "password123")))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void loginReturnsAccessAndRefreshTokens() {
        Account account = account();
        when(accountRepository.findByUsername("user01")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password123", account.getPassword())).thenReturn(true);
        when(refreshTokenService.issue(account)).thenReturn("refresh-1");
        when(jwtService.issue(1L, "user01", "ROLE_USER")).thenReturn("access-1");

        AuthService.AuthResult result = authService.login(new LoginRequest("user01", "password123"));

        assertThat(result.accessToken()).isEqualTo("access-1");
        assertThat(result.refreshToken()).isEqualTo("refresh-1");
        assertThat(result.account().id()).isEqualTo(1L);
        assertThat(result.account().roleName()).isEqualTo("ROLE_USER");
    }

    @Test
    void loginRejectsDisabledAccount() {
        Account account = account();
        account.setStatus((short) 0);
        when(accountRepository.findByUsername("user01")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password123", account.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user01", "password123")))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void refreshRotatesTokenAndIssuesNewAccessToken() {
        Account account = account();
        when(refreshTokenService.rotate("old-refresh"))
                .thenReturn(new RefreshTokenService.Rotation(account, "new-refresh"));
        when(jwtService.issue(1L, "user01", "ROLE_USER")).thenReturn("new-access");

        AuthService.AuthResult result = authService.refresh("old-refresh");

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void refreshPropagatesReuseDetection() {
        when(refreshTokenService.rotate("revoked"))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected"));

        assertThatThrownBy(() -> authService.refresh("revoked"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Refresh token reuse detected");
    }

    @Test
    void meMapsAccount() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account()));

        assertThat(authService.me(1L).email()).isEqualTo("user01@example.com");
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
        account.setAccountCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        account.setAccountUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        account.setRole(role);
        return account;
    }
}
