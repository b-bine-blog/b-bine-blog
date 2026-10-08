package com.mainblog.blog.domain;

import com.mainblog.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 일부 공개 블로그의 공유 링크(blog_share_links) (T007, FR-027, FR-028). 블로그당 active 1개. */
@Entity
@Table(name = "blog_share_links")
public class BlogShareLink extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blog_id", nullable = false)
    private Long blogId;

    @Column(name = "token", nullable = false)
    private String token;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected BlogShareLink() {
    }

    public BlogShareLink(Long blogId, String token) {
        this.blogId = blogId;
        this.token = token;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public Long getBlogId() {
        return blogId;
    }

    public String getToken() {
        return token;
    }

    public boolean isActive() {
        return active;
    }
}
