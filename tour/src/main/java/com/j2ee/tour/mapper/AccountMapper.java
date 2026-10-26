package com.j2ee.tour.mapper;

import com.j2ee.tour.dto.response.AccountResponse;
import com.j2ee.tour.model.Account;

public final class AccountMapper {

    private AccountMapper() {
    }

    public static AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getAccountId(),
                account.getUsername(),
                account.getAccountEmail(),
                account.getStatus() == null ? null : account.getStatus().intValue(),
                account.getRole().getRoleName()
        );
    }
}
