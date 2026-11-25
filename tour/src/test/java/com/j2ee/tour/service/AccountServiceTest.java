package com.j2ee.tour.service;

import com.j2ee.tour.dto.request.AccountUpdateRequest;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.AccountRoleRepository;
import com.j2ee.tour.security.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long USER_ID = 2L;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountRoleRepository accountRoleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, accountRoleRepository, passwordEncoder);
    }

    @Test
    void demotingTheOnlyActiveAdminIsRejectedWithConflict() {
        Account admin = account(ADMIN_ID, "admin", role(1L, "ROLE_ADMIN"));
        AccountRole userRole = role(2L, "ROLE_USER");
        when(accountRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(accountRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(accountRoleRepository.findById(2L)).thenReturn(Optional.of(userRole));
        when(accountRepository.findActiveAdminsForUpdate("ROLE_ADMIN", 1)).thenReturn(List.of(admin));

        assertThatThrownBy(() -> accountService.update(ADMIN_ID,
                new AccountUpdateRequest("admin", null, "admin@example.com", 1, 1, 2L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot remove the last active administrator");

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void demotingAnAdminWhenTwoActiveAdminsExistSucceeds() {
        Account admin = account(ADMIN_ID, "admin", role(1L, "ROLE_ADMIN"));
        Account otherAdmin = account(3L, "admin2", role(1L, "ROLE_ADMIN"));
        AccountRole userRole = role(2L, "ROLE_USER");
        when(accountRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(accountRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(accountRoleRepository.findById(2L)).thenReturn(Optional.of(userRole));
        when(accountRepository.findActiveAdminsForUpdate("ROLE_ADMIN", 1))
                .thenReturn(List.of(admin, otherAdmin));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = accountService.update(ADMIN_ID,
                new AccountUpdateRequest("admin", null, "admin@example.com", 1, 1, 2L));

        assertThat(response.roleId()).isEqualTo(2L);
        assertThat(response.roleName()).isEqualTo("ROLE_USER");
        verify(accountRepository).save(admin);
    }

    @Test
    void lockingTheOnlyActiveAdminIsRejectedWithConflict() {
        Account admin = account(ADMIN_ID, "admin", role(1L, "ROLE_ADMIN"));
        AccountRole adminRole = role(1L, "ROLE_ADMIN");
        when(accountRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(accountRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(accountRoleRepository.findById(1L)).thenReturn(Optional.of(adminRole));
        when(accountRepository.findActiveAdminsForUpdate("ROLE_ADMIN", 1)).thenReturn(List.of(admin));

        assertThatThrownBy(() -> accountService.update(ADMIN_ID,
                new AccountUpdateRequest("admin", null, "admin@example.com", 1, 0, 1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot remove the last active administrator");

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void updatingANonAdminAccountDoesNotRequireActiveAdminCount() {
        Account user = account(USER_ID, "user", role(2L, "ROLE_USER"));
        AccountRole userRole = role(2L, "ROLE_USER");
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(accountRepository.findByUsername("user")).thenReturn(Optional.empty());
        when(accountRoleRepository.findById(2L)).thenReturn(Optional.of(userRole));
        when(accountRepository.save(user)).thenReturn(user);

        accountService.update(USER_ID,
                new AccountUpdateRequest("user", null, "user@example.com", 1, 1, 2L));

        verify(accountRepository, never()).findActiveAdminsForUpdate("ROLE_ADMIN", 1);
        verify(accountRepository).save(user);
    }

    @Test
    void adminCannotDemoteOrDisableTheirOwnAccount() {
        when(accountRoleRepository.findById(3L)).thenReturn(Optional.of(role(3L, "ROLE_STAFF")));
        var caller = new AuthPrincipal(ADMIN_ID, "admin", "ROLE_ADMIN");
        assertThatThrownBy(() -> accountService.update(ADMIN_ID,
                new AccountUpdateRequest("admin", null, "admin@example.com", 1, 1, 3L), caller))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("your own");
        assertThatThrownBy(() -> accountService.update(ADMIN_ID,
                new AccountUpdateRequest("admin", null, "admin@example.com", 1, 0, 1L), caller))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("your own");
        verify(accountRepository, never()).save(any());
    }

    @Test
    void adminCanAssignStaffToAnotherCustomer() {
        Account user = account(USER_ID, "user", role(2L, "ROLE_USER"));
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(accountRoleRepository.findById(3L)).thenReturn(Optional.of(role(3L, "ROLE_STAFF")));
        when(accountRepository.save(user)).thenReturn(user);
        var result = accountService.update(USER_ID,
                new AccountUpdateRequest("user", null, "user@example.com", 1, 1, 3L),
                new AuthPrincipal(ADMIN_ID, "admin", "ROLE_ADMIN"));
        assertThat(result.roleName()).isEqualTo("ROLE_STAFF");
        assertThat(result.roleId()).isEqualTo(3L);
    }

    @Test
    void adminCannotDeleteTheirOwnAccount() {
        assertThatThrownBy(() -> accountService.delete(ADMIN_ID,
                new AuthPrincipal(ADMIN_ID, "admin", "ROLE_ADMIN")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("your own");
        verify(accountRepository, never()).delete(any());
    }

    @Test
    void deletingTheOnlyActiveAdminIsRejected() {
        Account admin = account(ADMIN_ID, "admin", role(1L, "ROLE_ADMIN"));
        when(accountRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(accountRepository.findActiveAdminsForUpdate("ROLE_ADMIN", 1)).thenReturn(List.of(admin));

        assertThatThrownBy(() -> accountService.delete(ADMIN_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot remove the last active administrator");

        verify(accountRepository, never()).delete(any(Account.class));
    }

    @Test
    void deletingAnAdminWhenTwoActiveAdminsExistSucceeds() {
        Account admin = account(ADMIN_ID, "admin", role(1L, "ROLE_ADMIN"));
        Account otherAdmin = account(3L, "admin2", role(1L, "ROLE_ADMIN"));
        when(accountRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(accountRepository.findActiveAdminsForUpdate("ROLE_ADMIN", 1))
                .thenReturn(List.of(admin, otherAdmin));

        accountService.delete(ADMIN_ID);

        verify(accountRepository).delete(admin);
    }

    @Test
    void deletingANonAdminDoesNotRequireActiveAdminCount() {
        Account user = account(USER_ID, "user", role(2L, "ROLE_USER"));
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        accountService.delete(USER_ID);

        verify(accountRepository, never()).findActiveAdminsForUpdate("ROLE_ADMIN", 1);
        verify(accountRepository).delete(user);
    }

    private AccountRole role(Long id, String name) {
        AccountRole role = new AccountRole();
        role.setAccountRoleId(id);
        role.setRoleName(name);
        return role;
    }

    private Account account(Long id, String username, AccountRole role) {
        Account account = new Account();
        account.setAccountId(id);
        account.setUsername(username);
        account.setPassword("$2a$10$hash");
        account.setAccountEmail(username + "@example.com");
        account.setEmailVerified(1);
        account.setStatus(1);
        account.setAccountCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        account.setAccountUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        account.setRole(role);
        return account;
    }
}
