package com.mainblog.blog.domain;

/** 참여 신청 상태 (data-model blog_join_requests) */
public enum JoinRequestStatus {
    PENDING, APPROVED, REJECTED, CANCELED, AUTO_REJECTED
}
