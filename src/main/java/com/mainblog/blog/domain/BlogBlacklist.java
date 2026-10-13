package com.mainblog.blog.domain;

import com.mainblog.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 블랙리스트(blog_blacklists) (T007, BLG-11, FR-057, FR-059).
 * 이름·이메일·전화번호는 HMAC 해시로만 둔다(research R-09). 해제 문의 승인 말고는 고치지 않는다.
 */
@Entity
@Table(name = "blog_blacklists")
public class BlogBlacklist extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sanction_id", nullable = false)
    private Long sanctionId;

    @Column(name = "blog_id", nullable = false)
    private Long blogId;

    @Column(name = "name_hash", nullable = false)
    private String nameHash;

    @Column(name = "email_hash", nullable = false)
    private String emailHash;

    @Column(name = "phone_hash", nullable = false)
    private String phoneHash;

    @Column(name = "released", nullable = false)
    private boolean released;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    protected BlogBlacklist() {
    }

    public BlogBlacklist(Long blogId, Long sanctionId, String nameHash, String emailHash, String phoneHash) {
        this.blogId = blogId;
        this.sanctionId = sanctionId;
        this.nameHash = nameHash;
        this.emailHash = emailHash;
        this.phoneHash = phoneHash;
    }

    public void release(LocalDateTime now) {
        this.released = true;
        this.releasedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getSanctionId() {
        return sanctionId;
    }

    public Long getBlogId() {
        return blogId;
    }

    public String getNameHash() {
        return nameHash;
    }

    public String getEmailHash() {
        return emailHash;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public boolean isReleased() {
        return released;
    }

    public LocalDateTime getReleasedAt() {
        return releasedAt;
    }
}
