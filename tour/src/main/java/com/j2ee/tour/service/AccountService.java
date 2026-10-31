package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.AccountCreateRequest;
import com.j2ee.tour.dto.request.AccountUpdateRequest;
import com.j2ee.tour.dto.response.AccountResponse;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.AccountRoleRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accountRepository,
                          AccountRoleRepository accountRoleRepository,
                          PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> list(Pageable pageable) {
        return PageResponse.from(accountRepository.findAll(pageable), AccountResponse::from);
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Long id) {
        return AccountResponse.from(find(id));
    }

    @Transactional
    public AccountResponse create(AccountCreateRequest request) {
        if (accountRepository.existsByUsername(request.username())) {
            throw new IllegalStateException("Username is already in use");
        }
        Account account = new Account();
        LocalDateTime now = LocalDateTime.now();
        account.setAccountCreatedAt(now);
        account.setAccountUpdatedAt(now);
        account.setUsername(request.username());
        account.setPassword(passwordEncoder.encode(request.password()));
        account.setAccountEmail(request.email());
        account.setEmailVerified(request.emailVerified());
        account.setStatus(request.status());
        account.setRole(resolveRole(request.roleId()));
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse update(Long id, AccountUpdateRequest request) {
        Account account = find(id);
        accountRepository.findByUsername(request.username())
                .filter(existing -> !existing.getAccountId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalStateException("Username is already in use");
                });
        account.setUsername(request.username());
        if (request.password() != null && !request.password().isBlank()) {
            account.setPassword(passwordEncoder.encode(request.password()));
        }
        account.setAccountEmail(request.email());
        account.setEmailVerified(request.emailVerified());
        account.setStatus(request.status());
        account.setRole(resolveRole(request.roleId()));
        account.setAccountUpdatedAt(LocalDateTime.now());
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public void delete(Long id) {
        accountRepository.delete(find(id));
    }

    private AccountRole resolveRole(Long roleId) {
        return accountRoleRepository.findById(roleId)
                .orElseThrow(() -> new NoSuchElementException("Account role not found"));
    }

    private Account find(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Account not found"));
    }
}
