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
 * 블로그 역할(blog_members) (T005, FR-033, FR-040, FR-043).
 * 블로그장은 생성 컬럼 owner_blog_id의 유니크로 블로그당 1명이다. 부블로그장 권한은 영역 3개(D-98 #18).
 */
@Entity
@Table(name = "blog_members")
public class BlogMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "blog_id", nullable = false)
    private Long blogId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false)
    private BlogRole role;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "manager_since")
    private LocalDateTime managerSince;

    @Column(name = "perm_blog_info", nullable = false)
    private boolean permBlogInfo;

    @Column(name = "perm_member_manage", nullable = false)
    private boolean permMemberManage;

    @Column(name = "perm_post_manage", nullable = false)
    private boolean permPostManage;

    /** DB가 계산하는 생성 컬럼. 읽기만 한다. */
    @Column(name = "owner_blog_id", insertable = false, updatable = false)
    private Long ownerBlogId;

    protected BlogMember() {
    }

    public static BlogMember owner(Long blogId, Long userId, LocalDateTime now) {
        return of(blogId, userId, BlogRole.OWNER, now);
    }

    public static BlogMember member(Long blogId, Long userId, LocalDateTime now) {
        return of(blogId, userId, BlogRole.MEMBER, now);
    }

    private static BlogMember of(Long blogId, Long userId, BlogRole role, LocalDateTime now) {
        BlogMember m = new BlogMember();
        m.blogId = blogId;
        m.userId = userId;
        m.role = role;
        m.joinedAt = now;
        return m;
    }

    /** 부블로그장 지정·권한 변경(FR-039, FR-040). 처음 지정할 때만 manager_since를 남긴다(FR-043). */
    public void makeManager(boolean blogInfo, boolean memberManage, boolean postManage, LocalDateTime now) {
        if (role != BlogRole.MANAGER) {
            this.managerSince = now;
        }
        this.role = BlogRole.MANAGER;
        this.permBlogInfo = blogInfo;
        this.permMemberManage = memberManage;
        this.permPostManage = postManage;
    }

    /** 일반 멤버로(부블로그장 해제, 위임한 이전 블로그장, 권한 박탈). */
    public void makeMember() {
        this.role = BlogRole.MEMBER;
        clearManager();
    }

    /** 블로그장으로(위임 수락·자동 승계). 부블로그장 지정·권한은 없어진다(D-98 #27). */
    public void makeOwner() {
        this.role = BlogRole.OWNER;
        clearManager();
    }

    private void clearManager() {
        this.managerSince = null;
        this.permBlogInfo = false;
        this.permMemberManage = false;
        this.permPostManage = false;
    }

    public boolean isOwner() {
        return role == BlogRole.OWNER;
    }

    public boolean isManager() {
        return role == BlogRole.MANAGER;
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

    public BlogRole getRole() {
        return role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public LocalDateTime getManagerSince() {
        return managerSince;
    }

    public boolean isPermBlogInfo() {
        return permBlogInfo;
    }

    public boolean isPermMemberManage() {
        return permMemberManage;
    }

    public boolean isPermPostManage() {
        return permPostManage;
    }
}
