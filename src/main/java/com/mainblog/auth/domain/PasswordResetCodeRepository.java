package com.mainblog.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    /** 회원당 한 건이지만 동시 발송으로 두 건이 생겨도 최신 것만 본다. */
    Optional<PasswordResetCode> findFirstByUserIdOrderByIdDesc(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PasswordResetCode c where c.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
