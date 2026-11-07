package com.j2ee.tour.repository;

import com.j2ee.tour.model.AuthToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from AuthToken token "
            + "where token.tokenHash = :tokenHash and token.tokenType = :tokenType")
    Optional<AuthToken> findForUpdate(@Param("tokenHash") String tokenHash,
                                      @Param("tokenType") String tokenType);

    @Modifying
    @Query("delete from AuthToken token "
            + "where token.account.accountId = :accountId and token.tokenType = :tokenType")
    int deleteByAccountIdAndType(@Param("accountId") Long accountId,
                                 @Param("tokenType") String tokenType);
}
