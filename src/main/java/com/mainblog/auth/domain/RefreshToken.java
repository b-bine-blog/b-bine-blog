package com.mainblog.auth.domain;

import com.mainblog.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Refresh Token(refresh_tokens). 기기(로그인)마다 한 건, 원문 대신 SHA-256 해시만 저장(R-03, D-82).
 * 유효 조건: revoked = false, expires_at > now, (remember_me 또는 last_active_at + 30분 > now) — data-model.md.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "remember_me", nullable = false)
    private boolean rememberMe;

    @Column(name = "last_active_at", nullable = false)
    private LocalDateTime lastActiveAt;

    /** 로그인 시각 + 14일 고정. 회전해도 이어받는다(FR-023, D-97). */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    protected RefreshToken() {
    }

    public RefreshToken(Long userId, String tokenHash, boolean rememberMe, LocalDateTime now, LocalDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.rememberMe = rememberMe;
        this.lastActiveAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(LocalDateTime now, Duration idleTimeout) {
        return !revoked
                && expiresAt.isAfter(now)
                && (rememberMe || lastActiveAt.plus(idleTimeout).isAfter(now));
    }

    public void touch(LocalDateTime now) {
        this.lastActiveAt = now;
    }

    public void revoke() {
        this.revoked = true;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public boolean isRememberMe() {
        return rememberMe;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }
}
