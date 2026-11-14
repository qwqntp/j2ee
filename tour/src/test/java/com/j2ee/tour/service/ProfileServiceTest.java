package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.request.ProfileRequest;
import com.j2ee.tour.dto.response.ProfileResponse;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.model.Customer;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-26T00:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private EmailService emailService;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(accountRepository, customerRepository,
                authTokenService, emailService, CLOCK);
    }

    @Test
    void getProfileWithoutCustomerReturnsAccountFieldsAndNullCustomerFields() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account()));
        when(customerRepository.findByAccountAccountId(1L)).thenReturn(Optional.empty());

        ProfileResponse response = profileService.getProfile(1L);

        assertThat(response.customerId()).isNull();
        assertThat(response.name()).isNull();
        assertThat(response.phone()).isNull();
        assertThat(response.address()).isNull();
        assertThat(response.dateOfBirth()).isNull();
        assertThat(response.username()).isEqualTo("user01");
        assertThat(response.email()).isEqualTo("user01@example.com");
        assertThat(response.emailVerified()).isZero();
    }

    @Test
    void getProfileReturnsCustomerFields() {
        Customer customer = customer();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account()));
        when(customerRepository.findByAccountAccountId(1L)).thenReturn(Optional.of(customer));

        ProfileResponse response = profileService.getProfile(1L);

        assertThat(response.customerId()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Nguyen Van A");
        assertThat(response.phone()).isEqualTo("0901234567");
        assertThat(response.address()).isEqualTo("Da Nang");
        assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 2));
    }

    @Test
    void getProfileRejectsUnknownAccount() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getProfile(99L))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void updateCreatesCustomerWhenMissingAndMirrorsAccountEmail() {
        Account account = account();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(customerRepository.findByAccountAccountId(1L)).thenReturn(Optional.empty());

        profileService.updateProfile(1L, new ProfileRequest(
                "  Nguyen Van A  ", null, " 0901234567 ", " Da Nang ",
                LocalDate.of(1990, 1, 2)));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        Customer saved = captor.getValue();
        assertThat(saved.getAccount()).isSameAs(account);
        assertThat(saved.getCustomerName()).isEqualTo("Nguyen Van A");
        assertThat(saved.getCustomerEmail()).isEqualTo("user01@example.com");
        assertThat(saved.getCustomerPhone()).isEqualTo("0901234567");
        assertThat(saved.getCustomerAddress()).isEqualTo("Da Nang");
        assertThat(saved.getCustomerDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 2));
        verifyNoInteractions(authTokenService, emailService);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void updateChangedEmailResetsVerificationAndReusesVerificationFlow() {
        Account account = account();
        account.setEmailVerified(1);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(customerRepository.findByAccountAccountId(1L)).thenReturn(Optional.of(customer()));
        when(accountRepository.existsByAccountEmailIgnoreCaseAndAccountIdNot(
                "New@Example.com", 1L)).thenReturn(false);
        when(authTokenService.issue(account, AuthTokenService.EMAIL_VERIFICATION))
                .thenReturn("verify-token");

        ProfileResponse response = profileService.updateProfile(1L, new ProfileRequest(
                "Nguyen Van A", " New@Example.com ", null, null, null));

        assertThat(account.getAccountEmail()).isEqualTo("New@Example.com");
        assertThat(account.getEmailVerified()).isZero();
        assertThat(account.getAccountUpdatedAt()).isEqualTo(NOW);
        assertThat(response.email()).isEqualTo("New@Example.com");
        verify(accountRepository).save(account);
        verify(authTokenService).issue(account, AuthTokenService.EMAIL_VERIFICATION);
        verify(emailService).sendEmailVerification("New@Example.com", "verify-token");
    }

    @Test
    void updateSameEmailIgnoringCaseKeepsVerificationAndSendsNoToken() {
        Account account = account();
        account.setEmailVerified(1);
        Customer customer = customer();
        customer.setCustomerEmail("stale@example.com");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(customerRepository.findByAccountAccountId(1L)).thenReturn(Optional.of(customer));

        ProfileResponse response = profileService.updateProfile(1L, new ProfileRequest(
                "Nguyen Van A", "USER01@EXAMPLE.COM", null, null, null));

        assertThat(account.getAccountEmail()).isEqualTo("user01@example.com");
        assertThat(account.getEmailVerified()).isEqualTo(1);
        assertThat(account.getAccountUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(customer.getCustomerEmail()).isEqualTo("user01@example.com");
        assertThat(response.emailVerified()).isEqualTo(1);
        verifyNoInteractions(authTokenService, emailService);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void updateBlankEmailKeepsCurrentEmail() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account()));
        when(customerRepository.findByAccountAccountId(1L)).thenReturn(Optional.of(customer()));

        profileService.updateProfile(1L, new ProfileRequest(
                "Nguyen Van A", "   ", null, null, null));

        verifyNoInteractions(authTokenService, emailService);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void updateRejectsEmailOwnedByAnotherAccount() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account()));
        when(customerRepository.findByAccountAccountId(1L)).thenReturn(Optional.of(customer()));
        when(accountRepository.existsByAccountEmailIgnoreCaseAndAccountIdNot(
                "taken@example.com", 1L)).thenReturn(true);

        assertThatThrownBy(() -> profileService.updateProfile(1L, new ProfileRequest(
                "Nguyen Van A", "taken@example.com", null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Email already exists")
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(authTokenService, emailService);
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void updateRejectsFutureDateOfBirth() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account()));

        assertThatThrownBy(() -> profileService.updateProfile(1L, new ProfileRequest(
                "Nguyen Van A", null, null, null, LocalDate.now(CLOCK).plusDays(1))))
                .isInstanceOf(ApiException.class)
                .hasMessage("Date of birth must not be in the future")
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(customerRepository, authTokenService, emailService);
    }

    @Test
    void updateRejectsBlankName() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account()));

        assertThatThrownBy(() -> profileService.updateProfile(1L, new ProfileRequest(
                "   ", null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Name is required");

        verifyNoInteractions(customerRepository, authTokenService, emailService);
    }

    private Customer customer() {
        Customer customer = new Customer();
        customer.setCustomerId(7L);
        customer.setCustomerName("Nguyen Van A");
        customer.setCustomerEmail("user01@example.com");
        customer.setCustomerPhone("0901234567");
        customer.setCustomerAddress("Da Nang");
        customer.setCustomerDateOfBirth(LocalDate.of(1990, 1, 2));
        return customer;
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
