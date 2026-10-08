package com.mainblog.blog.domain;

import com.mainblog.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 블랙리스트 해제 문의(blacklist_appeals) (T007, BLG-12, FR-061~FR-063). 일치 여부만 저장하고 원문은 없다. */
@Entity
@Table(name = "blacklist_appeals")
public class BlacklistAppeal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blacklist_id", nullable = false)
    private Long blacklistId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "content")
    private String content;

    @Column(name = "name_matched", nullable = false)
    private boolean nameMatched;

    @Column(name = "phone_matched", nullable = false)
    private boolean phoneMatched;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false)
    private AppealStatus status;

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    protected BlacklistAppeal() {
    }

    public BlacklistAppeal(Long blacklistId, Long userId, String content, boolean nameMatched, boolean phoneMatched) {
        this.blacklistId = blacklistId;
        this.userId = userId;
        this.content = content;
        this.nameMatched = nameMatched;
        this.phoneMatched = phoneMatched;
        this.status = AppealStatus.PENDING;
    }

    public void process(AppealStatus result, Long by, LocalDateTime now) {
        this.status = result;
        this.processedBy = by;
        this.processedAt = now;
    }

    public boolean isPending() {
        return status == AppealStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Long getBlacklistId() {
        return blacklistId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getContent() {
        return content;
    }

    public boolean isNameMatched() {
        return nameMatched;
    }

    public boolean isPhoneMatched() {
        return phoneMatched;
    }

    public AppealStatus getStatus() {
        return status;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
