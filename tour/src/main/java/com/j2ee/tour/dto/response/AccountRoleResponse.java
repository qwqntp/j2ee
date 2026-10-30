package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.AccountRole;

public record AccountRoleResponse(
        Long id,
        String roleName
) {
    public static AccountRoleResponse from(AccountRole role) {
        return new AccountRoleResponse(role.getAccountRoleId(), role.getRoleName());
    }
}
