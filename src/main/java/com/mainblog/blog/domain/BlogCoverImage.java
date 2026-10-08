package com.mainblog.blog.domain;

import com.mainblog.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.SQLRestriction;

/** 블로그 대표 이미지(images, owner_type = BLOG_COVER) (T008, FR-007, D-75). */
@Entity
@Table(name = "images")
@SQLRestriction("owner_type = 'BLOG_COVER'")
public class BlogCoverImage extends BaseTimeEntity {

    private static final String OWNER_TYPE = "BLOG_COVER";
    private static final int ORIGINAL_NAME_MAX = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "owner_type", nullable = false)
    private String ownerType;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "stored_name", nullable = false)
    private String storedName;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected BlogCoverImage() {
    }

    public BlogCoverImage(Long blogId, Long uploaderId, String storedName, String originalName, String contentType,
                          int sizeBytes) {
        this.uploaderId = uploaderId;
        this.ownerType = OWNER_TYPE;
        this.ownerId = blogId;
        this.storedName = storedName;
        String name = originalName == null || originalName.isBlank() ? "cover" : originalName.strip();
        this.originalName = name.length() > ORIGINAL_NAME_MAX ? name.substring(0, ORIGINAL_NAME_MAX) : name;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getStoredName() {
        return storedName;
    }
}
