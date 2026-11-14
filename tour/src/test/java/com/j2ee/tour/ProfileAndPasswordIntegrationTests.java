package com.j2ee.tour;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.request.ChangePasswordRequest;
import com.j2ee.tour.dto.request.LoginRequest;
import com.j2ee.tour.dto.request.ProfileRequest;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.model.Customer;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.AccountRoleRepository;
import com.j2ee.tour.repository.AuthTokenRepository;
import com.j2ee.tour.repository.CustomerRepository;
import com.j2ee.tour.repository.RefreshTokenRepository;
import com.j2ee.tour.service.AuthService;
import com.j2ee.tour.service.AuthTokenService;
import com.j2ee.tour.service.ProfileService;
import com.j2ee.tour.service.RefreshTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileAndPasswordIntegrationTests {

    @Autowired private ProfileService profileService;
    @Autowired private AuthService authService;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountRoleRepository accountRoleRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EntityManager entityManager;

    private Account account;

    @BeforeEach
    void setUp() {
        AccountRole role = new AccountRole();
        role.setRoleName("ROLE_B2_" + UUID.randomUUID());
        role = accountRoleRepository.save(role);

        account = new Account();
        account.setUsername("b2-" + UUID.randomUUID());
        account.setPassword(passwordEncoder.encode("old-password"));
        account.setAccountEmail("b2-" + UUID.randomUUID() + "@example.com");
        account.setEmailVerified(1);
        account.setStatus(1);
        account.setAccountCreatedAt(LocalDateTime.now());
        account.setAccountUpdatedAt(LocalDateTime.now());
        account.setRole(role);
        account = accountRepository.save(account);
    }

    @Test
    void updateProfileCreatesCustomerAndMirrorsAccountEmail() {
        profileService.updateProfile(account.getAccountId(), new ProfileRequest(
                "  Nguyen Van A  ", null, " 0901234567 ", " Da Nang ",
                LocalDate.of(1990, 1, 2)));

        Customer customer = customerRepository
                .findByAccountAccountId(account.getAccountId()).orElseThrow();
        assertThat(customer.getCustomerName()).isEqualTo("Nguyen Van A");
        assertThat(customer.getCustomerEmail()).isEqualTo(account.getAccountEmail());
        assertThat(customer.getCustomerPhone()).isEqualTo("0901234567");
        assertThat(customer.getCustomerAddress()).isEqualTo("Da Nang");
        assertThat(customer.getAccount().getAccountId()).isEqualTo(account.getAccountId());
        assertThat(accountRepository.findById(account.getAccountId()).orElseThrow()
                .getEmailVerified()).isEqualTo(1);
    }

    @Test
    void updateExistingProfileReplacesFields() {
        Customer existing = new Customer();
        existing.setAccount(account);
        existing.setCustomerName("Old Name");
        existing.setCustomerEmail("stale@example.com");
        customerRepository.save(existing);

        profileService.updateProfile(account.getAccountId(), new ProfileRequest(
                "New Name", "   ", "0907654321", "Hue", LocalDate.of(1995, 5, 5)));

        Customer customer = customerRepository
                .findByAccountAccountId(account.getAccountId()).orElseThrow();
        assertThat(customer.getCustomerId()).isEqualTo(existing.getCustomerId());
        assertThat(customer.getCustomerName()).isEqualTo("New Name");
        assertThat(customer.getCustomerEmail()).isEqualTo(account.getAccountEmail());
        assertThat(customer.getCustomerPhone()).isEqualTo("0907654321");
        assertThat(customer.getCustomerAddress()).isEqualTo("Hue");
        assertThat(customer.getCustomerDateOfBirth()).isEqualTo(LocalDate.of(1995, 5, 5));
    }

    @Test
    void changingEmailResetsVerificationAndIssuesOneVerificationToken() {
        String newEmail = "b2-new-" + UUID.randomUUID() + "@example.com";

        profileService.updateProfile(account.getAccountId(), new ProfileRequest(
                "Nguyen Van A", newEmail, null, null, null));

        Account reloaded = accountRepository.findById(account.getAccountId()).orElseThrow();
        assertThat(reloaded.getAccountEmail()).isEqualTo(newEmail);
        assertThat(reloaded.getEmailVerified()).isZero();
        assertThat(reloaded.getAccountUpdatedAt())
                .isAfterOrEqualTo(account.getAccountCreatedAt());
        assertThat(verificationTokensForAccount()).hasSize(1);
    }

    @Test
    void sameEmailIgnoringCaseKeepsVerificationAndSendsNoToken() {
        profileService.updateProfile(account.getAccountId(), new ProfileRequest(
                "Nguyen Van A", account.getAccountEmail().toUpperCase(), null, null, null));

        Account reloaded = accountRepository.findById(account.getAccountId()).orElseThrow();
        assertThat(reloaded.getAccountEmail()).isEqualTo(account.getAccountEmail());
        assertThat(reloaded.getEmailVerified()).isEqualTo(1);
        assertThat(verificationTokensForAccount()).isEmpty();
    }

    @Test
    void changingPasswordRevokesEveryRefreshTokenAndReplacesIt() {
        String first = refreshTokenService.issue(account);
        String second = refreshTokenService.issue(account);

        AuthService.AuthResult result = authService.changePassword(
                account.getAccountId(),
                new ChangePasswordRequest("old-password", "new-password"));

        // revokeAllByAccountId is a bulk update; clear the stale first-level cache to read it back.
        entityManager.flush();
        entityManager.clear();

        List<com.j2ee.tour.model.RefreshToken> tokens = tokensForAccount();
        assertThat(tokens).hasSize(3);
        assertThat(tokens.stream()
                .filter(token -> token.getTokenHash()
                        .equals(RefreshTokenService.hash(first)))
                .allMatch(com.j2ee.tour.model.RefreshToken::isRevoked)).isTrue();
        assertThat(tokens.stream()
                .filter(token -> token.getTokenHash()
                        .equals(RefreshTokenService.hash(second)))
                .allMatch(com.j2ee.tour.model.RefreshToken::isRevoked)).isTrue();
        assertThat(tokens.stream().filter(token -> !token.isRevoked())).hasSize(1);
        assertThat(result.refreshToken()).isNotBlank();

        assertThatThrownBy(() -> authService.login(
                new LoginRequest(account.getUsername(), "old-password")))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThat(authService.login(
                new LoginRequest(account.getUsername(), "new-password"))
                .account().id()).isEqualTo(account.getAccountId());
    }

    @Test
    void newRefreshTokenFromPasswordChangeCanBeRotated() {
        AuthService.AuthResult result = authService.changePassword(
                account.getAccountId(),
                new ChangePasswordRequest("old-password", "new-password"));

        AuthService.AuthResult rotated = authService.refresh(result.refreshToken());

        assertThat(rotated.refreshToken()).isNotBlank().isNotEqualTo(result.refreshToken());
        assertThat(rotated.account().id()).isEqualTo(account.getAccountId());
    }

    @Test
    void wrongCurrentPasswordReturnsBadRequestAndKeepsOldHash() {
        String oldHash = account.getPassword();

        assertThatThrownBy(() -> authService.changePassword(
                account.getAccountId(),
                new ChangePasswordRequest("wrong-password", "new-password")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Current password is incorrect")
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(accountRepository.findById(account.getAccountId()).orElseThrow()
                .getPassword()).isEqualTo(oldHash);
    }

    private List<com.j2ee.tour.model.AuthToken> verificationTokensForAccount() {
        return authTokenRepository.findAll().stream()
                .filter(token -> token.getAccount().getAccountId().equals(account.getAccountId()))
                .filter(token -> AuthTokenService.EMAIL_VERIFICATION.equals(token.getTokenType()))
                .toList();
    }

    private List<com.j2ee.tour.model.RefreshToken> tokensForAccount() {
        return refreshTokenRepository.findAll().stream()
                .filter(token -> token.getAccount().getAccountId().equals(account.getAccountId()))
                .toList();
    }
}
