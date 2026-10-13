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
 * 가입 이메일 인증(email_verifications). 이메일(소문자)당 한 건, 다시 보내면 같은 행을 새 번호로 덮어 이전 번호를 무효로 한다
 * (T027, FR-004, FR-005, FR-007).
 */
@Entity
@Table(name = "email_verifications")
public class EmailVerification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "fail_count", nullable = false)
    private int failCount;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    protected EmailVerification() {
    }

    public EmailVerification(String email) {
        this.email = email;
    }

    /** 새 번호로 덮어쓴다. 틀린 횟수와 인증 완료 표시도 처음으로 돌린다. */
    public void issue(String codeHash, LocalDateTime now, Duration ttl) {
        this.codeHash = codeHash;
        this.sentAt = now;
        this.expiresAt = now.plus(ttl);
        this.failCount = 0;
        this.verifiedAt = null;
    }

    public boolean canResend(LocalDateTime now, Duration resendInterval) {
        return sentAt == null || !sentAt.plus(resendInterval).isAfter(now);
    }

    /** 만료됐거나 틀린 횟수가 다 찼으면 더 이상 쓸 수 없다. */
    public boolean isUnusable(LocalDateTime now, int maxFail) {
        return !expiresAt.isAfter(now) || failCount >= maxFail;
    }

    public void recordFailure() {
        this.failCount++;
    }

    public void markVerified(LocalDateTime now) {
        this.verifiedAt = now;
    }

    /** 인증을 마친 뒤 verifiedTtl 안인지(FR-007, D-97 30분). */
    public boolean isVerifiedWithin(LocalDateTime now, Duration verifiedTtl) {
        return verifiedAt != null && verifiedAt.plus(verifiedTtl).isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public int getFailCount() {
        return failCount;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }
}
