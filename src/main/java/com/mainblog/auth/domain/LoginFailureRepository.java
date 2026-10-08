package com.mainblog.auth.domain;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginFailureRepository extends JpaRepository<LoginFailure, Long> {

    Optional<LoginFailure> findByEmail(String email);

    /** 로그인 성공·잠금 해제 뒤·비밀번호 재설정 때 0으로 되돌린다(FR-020, D-97). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LoginFailure f where f.email = :email")
    int deleteByEmail(@Param("email") String email);

    /** 04:00 배치: 마지막 실패가 1일 지난 행 삭제(data-model.md). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LoginFailure f where f.lastFailedAt < :before")
    int deleteByLastFailedAtBefore(@Param("before") LocalDateTime before);
}
