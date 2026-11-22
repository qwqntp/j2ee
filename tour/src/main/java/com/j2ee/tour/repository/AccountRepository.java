package com.j2ee.tour.repository;

import com.j2ee.tour.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @EntityGraph(attributePaths = "role")
    Page<Account> findAll(Pageable pageable);

    Optional<Account> findByUsername(String username);

    Optional<Account> findByAccountEmailIgnoreCase(String accountEmail);

    Optional<Account> findByGoogleSub(String googleSub);

    boolean existsByUsername(String username);

    boolean existsByAccountEmailIgnoreCase(String accountEmail);

    boolean existsByAccountEmailIgnoreCaseAndAccountIdNot(String accountEmail, Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a join a.role role where role.roleName = :roleName and a.status = :status")
    List<Account> findActiveAdminsForUpdate(@Param("roleName") String roleName,
                                            @Param("status") Integer status);
}
