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
 * 로그인 실패 기록(login_failures). 입력한 이메일(소문자) 기준으로 세어 가입 여부를 드러내지 않는다
 * (SEC-03, SEC-13, D-97, R-05).
 */
@Entity
@Table(name = "login_failures")
public class LoginFailure extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "fail_count", nullable = false)
    private int failCount;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_failed_at", nullable = false)
    private LocalDateTime lastFailedAt;

    protected LoginFailure() {
    }

    public LoginFailure(String email, LocalDateTime now) {
        this.email = email;
        this.lastFailedAt = now;
    }

    /** 실패 1회 기록. lockAfter번째 실패면 그 시각 + lockDuration까지 잠근다(FR-020). */
    public void recordFailure(LocalDateTime now, int lockAfter, Duration lockDuration) {
        this.failCount++;
        this.lastFailedAt = now;
        if (failCount >= lockAfter) {
            this.lockedUntil = now.plus(lockDuration);
        }
    }

    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** 잠금 시간이 지났는지. 지났으면 행을 지워 0부터 다시 센다(D-97 2번). */
    public boolean isLockExpired(LocalDateTime now) {
        return lockedUntil != null && !lockedUntil.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public int getFailCount() {
        return failCount;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public LocalDateTime getLastFailedAt() {
        return lastFailedAt;
    }
}
