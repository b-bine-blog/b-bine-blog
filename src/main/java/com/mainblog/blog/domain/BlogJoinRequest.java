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

/** 참여 신청(blog_join_requests) (T007, BLG-04, BLG-05, data-model). */
@Entity
@Table(name = "blog_join_requests")
public class BlogJoinRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "blog_id", nullable = false)
    private Long blogId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false)
    private JoinRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by")
    private Long processedBy;

    protected BlogJoinRequest() {
    }

    public static BlogJoinRequest pending(Long blogId, Long userId, LocalDateTime now) {
        BlogJoinRequest r = new BlogJoinRequest();
        r.blogId = blogId;
        r.userId = userId;
        r.status = JoinRequestStatus.PENDING;
        r.requestedAt = now;
        return r;
    }

    /** 자유 참여(바로 승인)나 차단 자동 거절처럼 신청과 처리가 한 번에 끝나는 경우. */
    public static BlogJoinRequest processed(Long blogId, Long userId, JoinRequestStatus status, LocalDateTime now) {
        BlogJoinRequest r = pending(blogId, userId, now);
        r.status = status;
        r.processedAt = now;
        return r;
    }

    public void process(JoinRequestStatus result, Long by, LocalDateTime now) {
        this.status = result;
        this.processedBy = by;
        this.processedAt = now;
    }

    public boolean isPending() {
        return status == JoinRequestStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBlogId() {
        return blogId;
    }

    public JoinRequestStatus getStatus() {
        return status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
