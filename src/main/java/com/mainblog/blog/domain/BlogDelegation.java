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

/** 위임 요청(blog_delegations) (T007, BLG-08, D-31). 블로그당 PENDING 1개(research R-12). */
@Entity
@Table(name = "blog_delegations")
public class BlogDelegation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Column(name = "blog_id", nullable = false)
    private Long blogId;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false)
    private DelegationStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    protected BlogDelegation() {
    }

    public static BlogDelegation request(Long blogId, Long fromUserId, Long toUserId, LocalDateTime now,
                                         LocalDateTime expiresAt) {
        BlogDelegation d = new BlogDelegation();
        d.blogId = blogId;
        d.fromUserId = fromUserId;
        d.toUserId = toUserId;
        d.status = DelegationStatus.PENDING;
        d.requestedAt = now;
        d.expiresAt = expiresAt;
        return d;
    }

    public void finish(DelegationStatus result, LocalDateTime now) {
        this.status = result;
        this.respondedAt = now;
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public Long getBlogId() {
        return blogId;
    }

    public Long getFromUserId() {
        return fromUserId;
    }

    public DelegationStatus getStatus() {
        return status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }
}
