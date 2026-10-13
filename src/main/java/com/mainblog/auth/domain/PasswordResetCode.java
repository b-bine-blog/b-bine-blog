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
 * 비밀번호 재설정 인증번호(password_reset_codes). 회원당 한 건, 새로 보내면 이전 행을 지운다.
 * 30분 뒤 만료, 5번 틀리면 무효, 쓰이면 바로 삭제(T053, FR-031, SEC-05, D-25).
 */
@Entity
@Table(name = "password_reset_codes")
public class PasswordResetCode extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "fail_count", nullable = false)
    private int failCount;

    @Column(name = "used", nullable = false)
    private boolean used;

    protected PasswordResetCode() {
    }

    public PasswordResetCode(Long userId, String codeHash, LocalDateTime now, Duration ttl) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.sentAt = now;
        this.expiresAt = now.plus(ttl);
    }

    public boolean canResend(LocalDateTime now, Duration resendInterval) {
        return !sentAt.plus(resendInterval).isAfter(now);
    }

    /** 만료·5번 틀림·이미 쓴 번호는 더 이상 쓸 수 없다. */
    public boolean isUnusable(LocalDateTime now, int maxFail) {
        return used || !expiresAt.isAfter(now) || failCount >= maxFail;
    }

    public void recordFailure() {
        this.failCount++;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public int getFailCount() {
        return failCount;
    }
}
