package com.mainblog.member.domain;

import com.mainblog.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.SQLRestriction;

/**
 * 프로필 사진(images, owner_type = PROFILE) (T061, FR-043, D-75).
 * 파일은 FileStorage에 UUID 이름으로 두고, 원래 이름은 original_name에만 둔다(SEC-08).
 */
@Entity
@Table(name = "images")
@SQLRestriction("owner_type = 'PROFILE'")
public class ProfileImage extends BaseTimeEntity {

    private static final String OWNER_TYPE = "PROFILE";
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

    protected ProfileImage() {
    }

    /** 회원 본인이 올린 자기 사진. 올린 사람과 소속이 같다. */
    public ProfileImage(Long userId, String storedName, String originalName, String contentType, int sizeBytes) {
        this.uploaderId = userId;
        this.ownerType = OWNER_TYPE;
        this.ownerId = userId;
        this.storedName = storedName;
        String name = originalName == null || originalName.isBlank() ? "profile" : originalName.strip();
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

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
