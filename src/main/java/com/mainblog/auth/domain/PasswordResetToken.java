package com.mainblog.auth.domain;

import com.mainblog.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 이메일 찾기 결과 계정마다 발급하는 재설정 임시 토큰(password_reset_tokens). 원문은 응답에만 있고 DB에는 해시만 둔다.
 * 10분 유효, 쓰이면 바로 삭제(T066, FR-038, D-26).
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(Long userId, String tokenHash, LocalDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }
}
