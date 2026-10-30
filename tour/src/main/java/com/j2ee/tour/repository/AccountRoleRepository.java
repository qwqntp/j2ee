package com.j2ee.tour.repository;

import com.j2ee.tour.model.AccountRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRoleRepository extends JpaRepository<AccountRole, Long> {
    Optional<AccountRole> findByRoleName(String roleName);
}
