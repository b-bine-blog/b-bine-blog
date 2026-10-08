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

/**
 * 멤버 제재 기록(blog_sanctions) (T007, BLG-13, FR-055). 기록은 고치지 않고 쌓는다.
 * 정지 해제는 UNSUSPEND 행을 더한다(research R-10).
 */
@Entity
@Table(name = "blog_sanctions")
public class BlogSanction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blog_id", nullable = false)
    private Long blogId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "report_id")
    private Long reportId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "sanction_type", nullable = false)
    private SanctionType sanctionType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "suspend_period")
    private SuspendPeriod suspendPeriod;

    @Column(name = "reason")
    private String reason;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "handled_by", nullable = false)
    private Long handledBy;

    protected BlogSanction() {
    }

    public static BlogSanction of(Long blogId, Long userId, SanctionType type, SuspendPeriod period, String reason,
                                  Long handledBy, Long reportId, LocalDateTime now) {
        BlogSanction s = new BlogSanction();
        s.blogId = blogId;
        s.userId = userId;
        s.sanctionType = type;
        s.suspendPeriod = type == SanctionType.SUSPEND ? period : null;
        s.reason = reason;
        s.handledBy = handledBy;
        s.reportId = reportId;
        s.startsAt = now;
        if (type == SanctionType.SUSPEND && period.duration() != null) {
            s.endsAt = now.plus(period.duration());
        }
        return s;
    }

    public Long getId() {
        return id;
    }

    public Long getBlogId() {
        return blogId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getReportId() {
        return reportId;
    }

    public SanctionType getSanctionType() {
        return sanctionType;
    }

    public SuspendPeriod getSuspendPeriod() {
        return suspendPeriod;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public Long getHandledBy() {
        return handledBy;
    }
}
