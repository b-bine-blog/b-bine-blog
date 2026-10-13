package com.mainblog.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findByEmail(String email);

    @Modifying(flushAutomatically = true)
    @Query("delete from EmailVerification v where v.email = :email")
    int deleteByEmail(@Param("email") String email);
}
