package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.response.AccountResponse;
import com.j2ee.tour.mapper.AccountMapper;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final AccountRepository accountRepository;

    public PageResponse<AccountResponse> listAccounts(int page, int size) {
        Page<Account> accounts = accountRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "accountId")));
        return PageResponse.from(accounts, AccountMapper::toResponse);
    }
}
