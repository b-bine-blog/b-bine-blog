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

    /**
     * 실패 1회를 원자적으로 기록한다. 같은 이메일로 동시에 실패해도 횟수를 잃지 않는다.
     * lockAfter번째 실패면 lockedUntil까지 잠근다(FR-020). MySQL은 UPDATE 절을 왼쪽부터 계산하므로
     * locked_until 식의 fail_count는 이미 1 늘어난 값이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO login_failures (email, fail_count, last_failed_at, locked_until) "
            + "VALUES (:email, 1, :now, IF(1 >= :lockAfter, :lockedUntil, NULL)) "
            + "ON DUPLICATE KEY UPDATE fail_count = fail_count + 1, last_failed_at = :now, "
            + "locked_until = IF(fail_count >= :lockAfter, :lockedUntil, locked_until)", nativeQuery = true)
    int recordFailure(@Param("email") String email, @Param("now") LocalDateTime now,
                      @Param("lockAfter") int lockAfter, @Param("lockedUntil") LocalDateTime lockedUntil);

    /** 04:00 배치: 마지막 실패가 1일 지난 행 삭제(data-model.md). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LoginFailure f where f.lastFailedAt < :before")
    int deleteByLastFailedAtBefore(@Param("before") LocalDateTime before);
}
