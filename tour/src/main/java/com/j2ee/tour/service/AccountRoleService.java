package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.AccountRoleRequest;
import com.j2ee.tour.dto.response.AccountRoleResponse;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.repository.AccountRoleRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class AccountRoleService {
    private final AccountRoleRepository accountRoleRepository;

    public AccountRoleService(AccountRoleRepository accountRoleRepository) {
        this.accountRoleRepository = accountRoleRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountRoleResponse> list(Pageable pageable) {
        return PageResponse.from(accountRoleRepository.findAll(pageable), AccountRoleResponse::from);
    }

    @Transactional(readOnly = true)
    public AccountRoleResponse get(Long id) {
        return AccountRoleResponse.from(find(id));
    }

    @Transactional
    public AccountRoleResponse create(AccountRoleRequest request) {
        AccountRole role = new AccountRole();
        role.setRoleName(request.roleName());
        return AccountRoleResponse.from(accountRoleRepository.save(role));
    }

    @Transactional
    public AccountRoleResponse update(Long id, AccountRoleRequest request) {
        AccountRole role = find(id);
        role.setRoleName(request.roleName());
        return AccountRoleResponse.from(accountRoleRepository.save(role));
    }

    @Transactional
    public void delete(Long id) {
        accountRoleRepository.delete(find(id));
    }

    private AccountRole find(Long id) {
        return accountRoleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Account role not found"));
    }
}
