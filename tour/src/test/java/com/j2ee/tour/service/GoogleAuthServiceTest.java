package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.AccountRoleRepository;
import com.j2ee.tour.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

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
class GoogleAuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T00:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final String CLIENT_ID = "test-client.apps.googleusercontent.com";

    @Mock
    private GoogleIdTokenVerifierService verifier;

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

    private GoogleAuthService googleAuthService;

    @BeforeEach
    void setUp() {
        googleAuthService = new GoogleAuthService(
                verifier,
                accountRepository,
                roleRepository,
                refreshTokenService,
                passwordEncoder,
                jwtService,
                CLOCK);
    }

    @Test
    void createsANewAccountForAnUnknownGoogleIdentity() {
        stubNewAccount("new-user@example.com", "new-user");
        stubIssuedSession(99L, "new-user");

        AuthService.AuthResult result = googleAuthService.signIn("id-token");

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        Account saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("new-user");
        assertThat(saved.getAccountEmail()).isEqualTo("new-user@example.com");
        assertThat(saved.getGoogleSub()).isEqualTo("google-sub");
        assertThat(saved.getEmailVerified()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getPasswordSet()).isFalse();
        assertThat(saved.getPassword()).isEqualTo("random-hash");
        assertThat(saved.getRole().getRoleName()).isEqualTo("ROLE_USER");
        assertThat(saved.getAccountCreatedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(result.accessToken()).isEqualTo("access-99");
        assertThat(result.refreshToken()).isEqualTo("refresh-99");
        verify(refreshTokenService, never()).revokeAllForAccount(any());
    }

    @Test
    void signsInAnExistingGoogleSubWithoutTouchingItsPassword() {
        Account account = account();
        account.setGoogleSub("google-sub");
        when(verifier.verify("id-token"))
                .thenReturn(new GoogleIdTokenVerifierService.GoogleIdentity(
                        "google-sub", "someone-else@example.com", true));
        when(accountRepository.findByGoogleSub("google-sub")).thenReturn(Optional.of(account));
        stubIssuedSession(1L, "user01");

        AuthService.AuthResult result = googleAuthService.signIn("id-token");

        assertThat(result.account().id()).isEqualTo(1L);
        assertThat(account.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(account.getPasswordSet()).isTrue();
        verify(accountRepository, never()).save(any(Account.class));
        verify(accountRepository, never()).findByAccountEmailIgnoreCase(anyString());
        verify(refreshTokenService, never()).revokeAllForAccount(any());
    }

    @Test
    void googleLoginPreservesExistingStaffRole() {
        Account staff = account();
        staff.getRole().setRoleName("ROLE_STAFF");
        when(verifier.verify("id-token")).thenReturn(
                new GoogleIdTokenVerifierService.GoogleIdentity("staff-sub", "staff@example.com", true));
        when(accountRepository.findByGoogleSub("staff-sub")).thenReturn(Optional.of(staff));
        when(jwtService.issue(staff.getAccountId(), staff.getUsername(), "ROLE_STAFF")).thenReturn("staff-jwt");
        when(refreshTokenService.issue(staff)).thenReturn("staff-refresh");

        var result = googleAuthService.signIn("id-token");

        assertThat(result.account().roleName()).isEqualTo("ROLE_STAFF");
        assertThat(result.accessToken()).isEqualTo("staff-jwt");
        verifyNoInteractions(roleRepository);
    }

    @Test
    void linksAVerifiedEmailAccountWithoutReplacingItsPassword() {
        Account account = account();
        account.setEmailVerified(1);
        when(verifier.verify("id-token"))
                .thenReturn(new GoogleIdTokenVerifierService.GoogleIdentity(
                        "google-sub", "USER01@example.com", true));
        when(accountRepository.findByGoogleSub("google-sub")).thenReturn(Optional.empty());
        when(accountRepository.findByAccountEmailIgnoreCase("USER01@example.com"))
                .thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);
        stubIssuedSession(1L, "user01");

        googleAuthService.signIn("id-token");

        assertThat(account.getGoogleSub()).isEqualTo("google-sub");
        assertThat(account.getEmailVerified()).isEqualTo(1);
        assertThat(account.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(account.getPasswordSet()).isTrue();
        assertThat(account.getAccountUpdatedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        verify(passwordEncoder, never()).encode(anyString());
        verify(refreshTokenService, never()).revokeAllForAccount(any());
        verify(accountRepository).save(account);
    }

    @Test
    void linkingAnUnverifiedEmailReplacesThePasswordAndRevokesEveryRefreshToken() {
        Account account = account();
        account.setEmailVerified(0);
        when(verifier.verify("id-token"))
                .thenReturn(new GoogleIdTokenVerifierService.GoogleIdentity(
                        "google-sub", "user01@example.com", true));
        when(accountRepository.findByGoogleSub("google-sub")).thenReturn(Optional.empty());
        when(accountRepository.findByAccountEmailIgnoreCase("user01@example.com"))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.encode(anyString())).thenReturn("fresh-unusable-hash");
        when(accountRepository.save(account)).thenReturn(account);
        stubIssuedSession(1L, "user01");

        googleAuthService.signIn("id-token");

        assertThat(account.getPassword()).isEqualTo("fresh-unusable-hash");
        assertThat(account.getPasswordSet()).isFalse();
        assertThat(account.getGoogleSub()).isEqualTo("google-sub");
        assertThat(account.getEmailVerified()).isEqualTo(1);
        verify(refreshTokenService).revokeAllForAccount(1L);
        verify(accountRepository).save(account);
    }

    @Test
    void rejectsADisabledAccountMatchedByGoogleSub() {
        Account account = account();
        account.setGoogleSub("google-sub");
        account.setStatus(0);
        when(verifier.verify("id-token"))
                .thenReturn(new GoogleIdTokenVerifierService.GoogleIdentity(
                        "google-sub", "user01@example.com", true));
        when(accountRepository.findByGoogleSub("google-sub")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> googleAuthService.signIn("id-token"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Account is disabled")
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(refreshTokenService, jwtService);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void rejectsADisabledAccountMatchedByEmailBeforeLinking() {
        Account account = account();
        account.setStatus(0);
        when(verifier.verify("id-token"))
                .thenReturn(new GoogleIdTokenVerifierService.GoogleIdentity(
                        "google-sub", "user01@example.com", true));
        when(accountRepository.findByGoogleSub("google-sub")).thenReturn(Optional.empty());
        when(accountRepository.findByAccountEmailIgnoreCase("user01@example.com"))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> googleAuthService.signIn("id-token"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        assertThat(account.getGoogleSub()).isNull();
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void derivesAUniqueUsernameFromTheEmailLocalPart() {
        stubNewAccount("john@example.com", "john");
        when(accountRepository.existsByUsername("john")).thenReturn(true);
        when(accountRepository.existsByUsername("john2")).thenReturn(false);
        stubIssuedSession(99L, "john2");

        googleAuthService.signIn("id-token");

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("john2");
    }

    @Test
    void truncatesALongLocalPartBeforeAppendingTheCollisionSuffix() {
        String localPart = "a".repeat(50);
        stubNewAccount(localPart + "@example.com", localPart);
        when(accountRepository.existsByUsername(localPart)).thenReturn(true);
        String suffixed = "a".repeat(49) + "2";
        when(accountRepository.existsByUsername(suffixed)).thenReturn(false);
        stubIssuedSession(99L, suffixed);

        googleAuthService.signIn("id-token");

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo(suffixed);
        assertThat(suffixed).hasSize(50);
    }

    private void stubNewAccount(String email, String username) {
        when(verifier.verify("id-token"))
                .thenReturn(new GoogleIdTokenVerifierService.GoogleIdentity(
                        "google-sub", email, true));
        when(accountRepository.findByGoogleSub("google-sub")).thenReturn(Optional.empty());
        when(accountRepository.findByAccountEmailIgnoreCase(email)).thenReturn(Optional.empty());
        when(roleRepository.findByRoleName("ROLE_USER")).thenReturn(Optional.of(role()));
        when(passwordEncoder.encode(anyString())).thenReturn("random-hash");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account toSave = invocation.getArgument(0);
            toSave.setAccountId(99L);
            return toSave;
        });
    }

    private void stubIssuedSession(Long accountId, String username) {
        when(refreshTokenService.issue(any(Account.class))).thenReturn("refresh-" + accountId);
        when(jwtService.issue(eq(accountId), eq(username), eq("ROLE_USER")))
                .thenReturn("access-" + accountId);
    }

    private AccountRole role() {
        AccountRole role = new AccountRole();
        role.setAccountRoleId(2L);
        role.setRoleName("ROLE_USER");
        return role;
    }

    private Account account() {
        Account account = new Account();
        account.setAccountId(1L);
        account.setUsername("user01");
        account.setPassword("$2a$10$hash");
        account.setAccountEmail("user01@example.com");
        account.setEmailVerified(1);
        account.setStatus(1);
        account.setAccountCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        account.setAccountUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        account.setRole(role());
        return account;
    }
}
