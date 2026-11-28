package com.j2ee.tour.service;

import com.j2ee.tour.dto.request.AccountRoleRequest;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.repository.AccountRoleRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountRoleProtectionTest {
    @Test
    void roleCrudCannotBypassAdministratorProtection() {
        var repository = mock(AccountRoleRepository.class);
        var service = new AccountRoleService(repository);
        var role = new AccountRole();
        role.setAccountRoleId(1L);
        role.setRoleName("ROLE_ADMIN");
        when(repository.findById(1L)).thenReturn(Optional.of(role));
        assertThatThrownBy(() -> service.update(1L, new AccountRoleRequest("ROLE_STAFF")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
        verify(repository, never()).delete(any());
    }
}
