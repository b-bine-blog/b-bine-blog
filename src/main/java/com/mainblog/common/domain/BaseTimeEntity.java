package com.mainblog.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;

/** 모든 테이블의 생성·수정 일시(REQ-001). 값은 DB 기본값과 ON UPDATE가 채우므로 읽기만 한다. */
@MappedSuperclass
public abstract class BaseTimeEntity {

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
