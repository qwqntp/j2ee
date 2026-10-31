package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.request.LoginRequest;
import com.j2ee.tour.dto.request.RegisterRequest;
import com.j2ee.tour.dto.response.AccountResponse;
import com.j2ee.tour.mapper.AccountMapper;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.AccountRoleRepository;
import com.j2ee.tour.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEFAULT_ROLE = "ROLE_USER";
    private static final int ACTIVE = 1;
    private static final int BCRYPT_MAX_BYTES = 72;
    // BCrypt hash of an arbitrary value, used to keep unknown-user login timing similar.
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final AccountRepository accountRepository;
    private final AccountRoleRepository roleRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    @Transactional
    public AuthResult register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Password must be at most 72 bytes");
        }
        if (accountRepository.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already exists");
        }
        // The app check gives a friendly message; the DB unique index is the final guard.
        if (accountRepository.existsByAccountEmailIgnoreCase(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already exists");
        }

        AccountRole role = roleRepository.findByRoleName(DEFAULT_ROLE)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Default role is missing"));

        LocalDateTime now = LocalDateTime.now(clock);
        Account account = new Account();
        account.setUsername(request.username());
        account.setPassword(passwordEncoder.encode(request.password()));
        account.setAccountEmail(request.email());
        account.setEmailVerified(0);
        account.setStatus(ACTIVE);
        account.setAccountCreatedAt(now);
        account.setAccountUpdatedAt(now);
        account.setRole(role);
        accountRepository.save(account);

        return issue(account, refreshTokenService.issue(account));
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        Account account = accountRepository.findByUsername(request.username()).orElse(null);
        if (account == null) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        if (account.getStatus() == null || account.getStatus() != ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is disabled");
        }
        return issue(account, refreshTokenService.issue(account));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawToken);
        return issue(rotation.account(), rotation.refreshToken());
    }

    public void logout(String rawToken) {
        refreshTokenService.revoke(rawToken);
    }

    @Transactional(readOnly = true)
    public AccountResponse me(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found"));
        return AccountMapper.toResponse(account);
    }

    private AuthResult issue(Account account, String refreshToken) {
        String accessToken = jwtService.issue(
                account.getAccountId(),
                account.getUsername(),
                account.getRole().getRoleName());
        return new AuthResult(accessToken, refreshToken, AccountMapper.toResponse(account));
    }

    public record AuthResult(String accessToken, String refreshToken, AccountResponse account) {
    }
}
