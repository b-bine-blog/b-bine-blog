package com.mainblog.auth.domain;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 비밀번호 재설정·탈퇴·재사용 감지: 그 회원의 모든 기기 로그아웃(FR-033, FR-044). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.userId = :userId and t.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);

    /** 비밀번호 변경: 지금 기기는 남기고 다른 기기만 로그아웃(FR-029, D-97). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true "
            + "where t.userId = :userId and t.id <> :tokenId and t.revoked = false")
    int revokeAllByUserIdExcept(@Param("userId") Long userId, @Param("tokenId") Long tokenId);

    /** 04:00 배치: 만료·폐기된 행 삭제(D-83). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken t where t.expiresAt <= :now or t.revoked = true")
    int deleteExpiredOrRevoked(@Param("now") LocalDateTime now);
}
