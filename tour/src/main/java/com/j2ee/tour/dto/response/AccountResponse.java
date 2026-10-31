package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.Account;

import java.time.LocalDateTime;

public record AccountResponse(
        Long id,
        String username,
        String email,
        Integer emailVerified,
        Integer status,
        Long roleId,
        String roleName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public AccountResponse(Long id, String username, String email, Integer status, String roleName) {
        this(id, username, email, null, status, null, roleName, null, null);
    }

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getAccountId(),
                account.getUsername(),
                account.getAccountEmail(),
                account.getEmailVerified(),
                account.getStatus(),
                account.getRole() == null ? null : account.getRole().getAccountRoleId(),
                account.getRole() == null ? null : account.getRole().getRoleName(),
                account.getAccountCreatedAt(),
                account.getAccountUpdatedAt()
        );
    }
}
