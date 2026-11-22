package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.AccountRoleRepository;
import com.j2ee.tour.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Resolves a verified Google identity to an account. {@code google_sub} is the
 * identity key; the email is only used once to link an existing password account.
 */
@Service
public class GoogleAuthService {

    private static final String DEFAULT_ROLE = "ROLE_USER";
    private static final int ACTIVE = 1;
    private static final int MAX_USERNAME_LENGTH = 50;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GoogleIdTokenVerifierService verifier;
    private final AccountRepository accountRepository;
    private final AccountRoleRepository roleRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public GoogleAuthService(GoogleIdTokenVerifierService verifier,
                             AccountRepository accountRepository,
                             AccountRoleRepository roleRepository,
                             RefreshTokenService refreshTokenService,
                             PasswordEncoder passwordEncoder,
                             JwtService jwtService,
                             Clock clock) {
        this.verifier = verifier;
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Transactional
    public AuthService.AuthResult signIn(String idToken) {
        GoogleIdTokenVerifierService.GoogleIdentity identity = verifier.verify(idToken);
        if (!identity.emailVerified()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Google email is not verified");
        }
        if (identity.email() == null || identity.email().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Google account has no email");
        }

        Account account = accountRepository.findByGoogleSub(identity.subject()).orElse(null);
        if (account != null) {
            requireActive(account);
            return issue(account);
        }

        account = accountRepository.findByAccountEmailIgnoreCase(identity.email()).orElse(null);
        if (account != null) {
            requireActive(account);
            linkExistingAccount(account, identity);
            return issue(account);
        }

        return issue(createAccount(identity));
    }

    private void requireActive(Account account) {
        if (account.getStatus() == null || account.getStatus() != ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is disabled");
        }
    }

    private void linkExistingAccount(Account account,
                                     GoogleIdTokenVerifierService.GoogleIdentity identity) {
        // SECURITY: an unverified existing account may have been pre-registered by someone
        // else who knows its password. Replace the hash with an unusable random value and
        // revoke every refresh token before attaching the Google identity.
        if (!Integer.valueOf(1).equals(account.getEmailVerified())) {
            account.setPassword(passwordEncoder.encode(randomUnusablePassword()));
            account.setPasswordSet(false);
            refreshTokenService.revokeAllForAccount(account.getAccountId());
        }
        account.setGoogleSub(identity.subject());
        account.setEmailVerified(1);
        account.setAccountUpdatedAt(LocalDateTime.now(clock));
        accountRepository.save(account);
    }

    private Account createAccount(GoogleIdTokenVerifierService.GoogleIdentity identity) {
        AccountRole role = roleRepository.findByRoleName(DEFAULT_ROLE)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Default role is missing"));

        LocalDateTime now = LocalDateTime.now(clock);
        Account account = new Account();
        account.setUsername(uniqueUsername(identity.email()));
        account.setPassword(passwordEncoder.encode(randomUnusablePassword()));
        account.setPasswordSet(false);
        account.setAccountEmail(identity.email());
        account.setGoogleSub(identity.subject());
        account.setEmailVerified(1);
        account.setStatus(ACTIVE);
        account.setAccountCreatedAt(now);
        account.setAccountUpdatedAt(now);
        account.setRole(role);
        return accountRepository.save(account);
    }

    private AuthService.AuthResult issue(Account account) {
        String accessToken = jwtService.issue(
                account.getAccountId(),
                account.getUsername(),
                account.getRole().getRoleName());
        return new AuthService.AuthResult(
                accessToken,
                refreshTokenService.issue(account),
                com.j2ee.tour.mapper.AccountMapper.toResponse(account));
    }

    private String uniqueUsername(String email) {
        int at = email.indexOf('@');
        String localPart = at > 0 ? email.substring(0, at) : email;
        String base = localPart.replaceAll("[^A-Za-z0-9._-]", "");
        if (base.isBlank()) {
            base = "google-user";
        }

        for (int suffixNumber = 1; suffixNumber < 10_000; suffixNumber++) {
            String suffix = suffixNumber == 1 ? "" : String.valueOf(suffixNumber);
            int maxBaseLength = MAX_USERNAME_LENGTH - suffix.length();
            String candidate = (base.length() > maxBaseLength
                    ? base.substring(0, maxBaseLength)
                    : base) + suffix;
            if (!accountRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, "Could not allocate a username");
    }

    private String randomUnusablePassword() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
