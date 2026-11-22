package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.request.ChangePasswordRequest;
import com.j2ee.tour.dto.request.LoginRequest;
import com.j2ee.tour.dto.request.RegisterRequest;
import com.j2ee.tour.dto.request.ResetPasswordRequest;
import com.j2ee.tour.dto.request.VerifyEmailRequest;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.model.AuthToken;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private AuthTokenService authTokenService;

    @Mock
    private EmailService emailService;

    private final AfterCommitMailDispatcher mailDispatcher = new AfterCommitMailDispatcher();

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
                authTokenService,
                emailService,
                mailDispatcher,
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
    void registerSendsEmailVerificationToken() {
        AccountRole role = account().getRole();
        when(accountRepository.existsByUsername("new-user")).thenReturn(false);
        when(accountRepository.existsByAccountEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(roleRepository.findByRoleName("ROLE_USER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(authTokenService.issue(any(Account.class), eq(AuthTokenService.EMAIL_VERIFICATION)))
                .thenReturn("verify-token");
        when(refreshTokenService.issue(any(Account.class))).thenReturn("refresh-1");
        when(jwtService.issue(any(), eq("new-user"), eq("ROLE_USER"))).thenReturn("access-1");

        authService.register(new RegisterRequest("new-user", "password123", "new@example.com"));

        verify(emailService).sendEmailVerification("new@example.com", "verify-token");
    }

    @Test
    void registerDefersTheVerificationEmailUntilCommit() {
        stubSuccessfulRegistration();

        TransactionSynchronizationManager.initSynchronization();
        try {
            authService.register(new RegisterRequest("new-user", "password123", "new@example.com"));

            verifyNoInteractions(emailService);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(emailService).sendEmailVerification("new@example.com", "verify-token");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rolledBackRegistrationSendsNoVerificationEmail() {
        stubSuccessfulRegistration();

        TransactionSynchronizationManager.initSynchronization();
        try {
            authService.register(new RegisterRequest("new-user", "password123", "new@example.com"));
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(
                            TransactionSynchronization.STATUS_ROLLED_BACK));

            verifyNoInteractions(emailService);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void forgotPasswordDefersTheResetEmailUntilCommit() {
        Account account = account();
        when(accountRepository.findByAccountEmailIgnoreCase("user01@example.com"))
                .thenReturn(Optional.of(account));
        when(authTokenService.issue(account, AuthTokenService.PASSWORD_RESET))
                .thenReturn("reset-token");

        TransactionSynchronizationManager.initSynchronization();
        try {
            authService.forgotPassword("user01@example.com");

            verifyNoInteractions(emailService);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(emailService).sendPasswordReset("user01@example.com", "reset-token");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void stubSuccessfulRegistration() {
        AccountRole role = account().getRole();
        when(accountRepository.existsByUsername("new-user")).thenReturn(false);
        when(accountRepository.existsByAccountEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(roleRepository.findByRoleName("ROLE_USER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(authTokenService.issue(any(Account.class), eq(AuthTokenService.EMAIL_VERIFICATION)))
                .thenReturn("verify-token");
        when(refreshTokenService.issue(any(Account.class))).thenReturn("refresh-1");
        when(jwtService.issue(any(), eq("new-user"), eq("ROLE_USER"))).thenReturn("access-1");
    }

    @Test
    void forgotPasswordDoesNotRevealUnknownEmail() {
        when(accountRepository.findByAccountEmailIgnoreCase("missing@example.com"))
                .thenReturn(Optional.empty());

        authService.forgotPassword("missing@example.com");

        verifyNoInteractions(authTokenService, emailService);
    }

    @Test
    void forgotPasswordSendsResetTokenForKnownEmail() {
        Account account = account();
        when(accountRepository.findByAccountEmailIgnoreCase("user01@example.com"))
                .thenReturn(Optional.of(account));
        when(authTokenService.issue(account, AuthTokenService.PASSWORD_RESET))
                .thenReturn("reset-token");

        authService.forgotPassword("user01@example.com");

        verify(emailService).sendPasswordReset("user01@example.com", "reset-token");
    }

    @Test
    void resetPasswordConsumesTokenChangesPasswordAndRevokesRefreshTokens() {
        Account account = account();
        when(authTokenService.consume("reset-token", AuthTokenService.PASSWORD_RESET))
                .thenReturn(authToken(account));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        authService.resetPassword(new ResetPasswordRequest("reset-token", "new-password"));

        assertThat(account.getPassword()).isEqualTo("new-hash");
        verify(accountRepository).save(account);
        verify(refreshTokenService).revokeAllForAccount(1L);
    }

    @Test
    void resetPasswordRejectsExpiredOrReusedToken() {
        when(authTokenService.consume("expired", AuthTokenService.PASSWORD_RESET))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));

        assertThatThrownBy(() ->
                authService.resetPassword(new ResetPasswordRequest("expired", "new-password")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid or expired token");

        verify(accountRepository, never()).save(any(Account.class));
        verifyNoInteractions(emailService);
    }

    @Test
    void verifyEmailConsumesTokenAndMarksAccountVerified() {
        Account account = account();
        when(authTokenService.consume("verify-token", AuthTokenService.EMAIL_VERIFICATION))
                .thenReturn(authToken(account));

        authService.verifyEmail(new VerifyEmailRequest("verify-token"));

        assertThat(account.getEmailVerified()).isEqualTo(1);
        verify(accountRepository).save(account);
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
        account.setStatus(0);
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

    @Test
    void resetPasswordMarksThePasswordAsUsable() {
        Account account = account();
        account.setPasswordSet(false);
        when(authTokenService.consume("reset-token", AuthTokenService.PASSWORD_RESET))
                .thenReturn(authToken(account));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        authService.resetPassword(new ResetPasswordRequest("reset-token", "new-password"));

        assertThat(account.getPasswordSet()).isTrue();
        assertThat(account.getPassword()).isEqualTo("new-hash");
    }

    @Test
    void changePasswordRejectsAnAccountWithoutAUsablePassword() {
        Account account = account();
        account.setPasswordSet(false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> authService.changePassword(1L,
                new ChangePasswordRequest("any-password", "new-password")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Forgot password")
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(passwordEncoder, refreshTokenService);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void registerMarksThePasswordAsUsable() {
        AccountRole role = account().getRole();
        when(accountRepository.existsByUsername("new-user")).thenReturn(false);
        when(accountRepository.existsByAccountEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(roleRepository.findByRoleName("ROLE_USER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(authTokenService.issue(any(Account.class), eq(AuthTokenService.EMAIL_VERIFICATION)))
                .thenReturn("verify-token");
        when(refreshTokenService.issue(any(Account.class))).thenReturn("refresh-1");
        when(jwtService.issue(any(), eq("new-user"), eq("ROLE_USER"))).thenReturn("access-1");

        authService.register(new RegisterRequest("new-user", "password123", "new@example.com"));

        org.mockito.ArgumentCaptor<Account> captor =
                org.mockito.ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordSet()).isTrue();
        assertThat(captor.getValue().getGoogleSub()).isNull();
    }

    @Test
    void changePasswordRejectsWrongCurrentPasswordWithBadRequest() {
        Account account = account();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong-password", account.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(1L,
                new ChangePasswordRequest("wrong-password", "new-password")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Current password is incorrect")
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(accountRepository, never()).save(any(Account.class));
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void changePasswordRejectsNewPasswordMatchingTheCurrentHash() {
        Account account = account();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("same-password", account.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(1L,
                new ChangePasswordRequest("same-password", "same-password")))
                .isInstanceOf(ApiException.class)
                .hasMessage("New password must be different from the current password");

        verify(accountRepository, never()).save(any(Account.class));
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void changePasswordRejectsNewPasswordOver72Utf8Bytes() {
        Account account = account();
        String newPassword = "€".repeat(25);
        assertThat(newPassword.getBytes(StandardCharsets.UTF_8)).hasSize(75);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("old-password", account.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(1L,
                new ChangePasswordRequest("old-password", newPassword)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Password must be at most 72 bytes");

        verify(accountRepository, never()).save(any(Account.class));
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void changePasswordAcceptsExactly72Utf8Bytes() {
        Account account = account();
        String newPassword = "a".repeat(72);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("old-password", account.getPassword())).thenReturn(true);
        when(passwordEncoder.matches(newPassword, account.getPassword())).thenReturn(false);
        when(passwordEncoder.encode(newPassword)).thenReturn("new-hash");
        when(refreshTokenService.issue(account)).thenReturn("refresh-2");
        when(jwtService.issue(1L, "user01", "ROLE_USER")).thenReturn("access-2");

        AuthService.AuthResult result = authService.changePassword(
                1L, new ChangePasswordRequest("old-password", newPassword));

        assertThat(result.refreshToken()).isEqualTo("refresh-2");
        assertThat(account.getPassword()).isEqualTo("new-hash");
    }

    @Test
    void changePasswordRevokesAllRefreshTokensAndReturnsNewPair() {
        Account account = account();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("old-password", account.getPassword())).thenReturn(true);
        when(passwordEncoder.matches("new-password", account.getPassword())).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(refreshTokenService.issue(account)).thenReturn("refresh-2");
        when(jwtService.issue(1L, "user01", "ROLE_USER")).thenReturn("access-2");

        AuthService.AuthResult result = authService.changePassword(
                1L, new ChangePasswordRequest("old-password", "new-password"));

        assertThat(account.getPassword()).isEqualTo("new-hash");
        assertThat(account.getAccountUpdatedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(result.accessToken()).isEqualTo("access-2");
        assertThat(result.refreshToken()).isEqualTo("refresh-2");
        verify(accountRepository).save(account);
        verify(refreshTokenService).revokeAllForAccount(1L);
        verify(refreshTokenService).issue(account);
    }

    @Test
    void changePasswordRejectsUnknownAccount() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword(99L,
                new ChangePasswordRequest("old-password", "new-password")))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(refreshTokenService);
    }

    private AuthToken authToken(Account account) {
        AuthToken token = new AuthToken();
        token.setAccount(account);
        token.setTokenType(AuthTokenService.PASSWORD_RESET);
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
        account.setEmailVerified(0);
        account.setStatus(1);
        account.setAccountCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        account.setAccountUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        account.setRole(role);
        return account;
    }
}
