package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.request.ProfileRequest;
import com.j2ee.tour.dto.response.ProfileResponse;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.Customer;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final AuthTokenService authTokenService;
    private final EmailService emailService;
    private final AfterCommitMailDispatcher mailDispatcher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long accountId) {
        Account account = account(accountId);
        Customer customer = customerRepository.findByAccountAccountId(accountId).orElse(null);
        return toResponse(account, customer);
    }

    /**
     * Upserts the customer row and mirrors the authoritative account email into it. Changing the
     * email resets verification and reuses the Batch 3 token + email flow; refresh tokens are
     * intentionally untouched (the password did not change).
     */
    @Transactional
    public ProfileResponse updateProfile(Long accountId, ProfileRequest request) {
        Account account = account(accountId);
        validate(request);

        String name = trim(request.name());
        String email = trim(request.email());
        Customer customer = customerRepository.findByAccountAccountId(accountId)
                .orElseGet(() -> {
                    Customer created = new Customer();
                    created.setAccount(account);
                    return created;
                });

        boolean emailChanged = email != null
                && (account.getAccountEmail() == null
                || !email.equalsIgnoreCase(account.getAccountEmail()));
        if (emailChanged) {
            if (accountRepository.existsByAccountEmailIgnoreCaseAndAccountIdNot(email, accountId)) {
                throw new ApiException(HttpStatus.CONFLICT, "Email already exists");
            }
            account.setAccountEmail(email);
            account.setEmailVerified(0);
            account.setAccountUpdatedAt(LocalDateTime.now(clock));
            accountRepository.save(account);
        }

        customer.setCustomerName(name);
        customer.setCustomerEmail(account.getAccountEmail());
        customer.setCustomerPhone(trim(request.phone()));
        customer.setCustomerAddress(trim(request.address()));
        customer.setCustomerDateOfBirth(request.dateOfBirth());
        customerRepository.save(customer);

        if (emailChanged) {
            String token = authTokenService.issue(account, AuthTokenService.EMAIL_VERIFICATION);
            mailDispatcher.dispatch(() ->
                    emailService.sendEmailVerification(account.getAccountEmail(), token));
        }

        return toResponse(account, customer);
    }

    private Account account(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found"));
    }

    private void validate(ProfileRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        if (request.dateOfBirth() != null
                && request.dateOfBirth().isAfter(LocalDate.now(clock))) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Date of birth must not be in the future");
        }
    }

    private ProfileResponse toResponse(Account account, Customer customer) {
        return new ProfileResponse(
                customer == null ? null : customer.getCustomerId(),
                account.getUsername(),
                account.getAccountEmail(),
                account.getEmailVerified(),
                customer == null ? null : customer.getCustomerName(),
                customer == null ? null : customer.getCustomerPhone(),
                customer == null ? null : customer.getCustomerAddress(),
                customer == null ? null : customer.getCustomerDateOfBirth(),
                account.getPasswordSet() == null || account.getPasswordSet(),
                account.getGoogleSub() != null && !account.getGoogleSub().isBlank());
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
