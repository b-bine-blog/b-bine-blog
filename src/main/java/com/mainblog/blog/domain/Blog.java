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
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 블로그(blogs) (T004). 규칙은 data-model.md "blogs".
 * 주소는 만든 뒤 바꿀 수 없고(FR-009), 폐쇄 30일 뒤 정리에서만 NULL로 비운다(FR-008, D-86).
 * member_count는 동시에 바뀌므로 BlogRepository.addMemberCount로만 고친다. 바뀐 컬럼만 UPDATE하게 해(@DynamicUpdate)
 * 다른 필드를 고칠 때 예전 멤버 수로 덮어쓰지 않게 한다.
 */
@Entity
@Table(name = "blogs")
@DynamicUpdate
public class Blog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "visibility", nullable = false)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "join_policy", nullable = false)
    private JoinPolicy joinPolicy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false)
    private BlogStatus status;

    @Column(name = "close_scheduled_at")
    private LocalDateTime closeScheduledAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "close_reason")
    private CloseReason closeReason;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "member_count", nullable = false)
    private int memberCount;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    protected Blog() {
    }

    /** 새 블로그. 블로그장 1명이 멤버 수에 들어간다(D-89). */
    public static Blog create(String name, String address, String description, Visibility visibility,
                              JoinPolicy joinPolicy) {
        Blog b = new Blog();
        b.name = name;
        b.address = address;
        b.description = description;
        b.visibility = visibility;
        b.joinPolicy = joinPolicy;
        b.status = BlogStatus.ACTIVE;
        b.memberCount = 1;
        return b;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changeDescription(String description) {
        this.description = description;
    }

    public void changeVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public void changeJoinPolicy(JoinPolicy joinPolicy) {
        this.joinPolicy = joinPolicy;
    }

    /** 폐쇄 예약(FR-073). 예정 시각 계산은 BlogClosingService가 한다. */
    public void scheduleClose(LocalDateTime at, CloseReason reason) {
        this.status = BlogStatus.CLOSING;
        this.closeScheduledAt = at;
        this.closeReason = reason;
    }

    /** 폐쇄 철회(FR-078). */
    public void withdrawClose() {
        this.status = BlogStatus.ACTIVE;
        this.closeScheduledAt = null;
        this.closeReason = null;
    }

    /** 04:00 처리가 폐쇄한다(FR-074). */
    public void close(LocalDateTime now) {
        this.status = BlogStatus.CLOSED;
        this.closedAt = now;
    }

    /** 관리자 숨김(005 FR-012, D-103 #7). 누가 숨겼는지는 admin_actions. */
    public void hide(LocalDateTime now) {
        this.hidden = true;
        this.hiddenAt = now;
    }

    public void unhide() {
        this.hidden = false;
        this.hiddenAt = null;
    }

    /** 이미 폐쇄 예정인 블로그를 관리자가 폐쇄: 시각은 그대로 두고 사유만 관리자 폐쇄로(005 D-103 #8). */
    public void markAdminClose() {
        this.closeReason = CloseReason.ADMIN;
    }

    public boolean isClosed() {
        return status == BlogStatus.CLOSED;
    }

    public boolean isClosing() {
        return status == BlogStatus.CLOSING;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getDescription() {
        return description;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public JoinPolicy getJoinPolicy() {
        return joinPolicy;
    }

    public BlogStatus getStatus() {
        return status;
    }

    public LocalDateTime getCloseScheduledAt() {
        return closeScheduledAt;
    }

    public CloseReason getCloseReason() {
        return closeReason;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public boolean isHidden() {
        return hidden;
    }

    public LocalDateTime getHiddenAt() {
        return hiddenAt;
    }
}
