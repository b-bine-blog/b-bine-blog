package com.mainblog.blog.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 블로그 응답 모양 (contracts/blog-api.yaml의 BlogSummary·BlogDetail·BlogPage·MyBlogItem). */
public final class BlogViews {

    private BlogViews() {
    }

    public record BlogSummary(Long id, String name, String address, String description, String coverImageUrl,
                              List<String> tags, int memberCount, LocalDateTime createdAt) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record BlogDetail(Long id, String name, String address, String description, String coverImageUrl,
                             List<String> tags, int memberCount, LocalDateTime createdAt,
                             String visibility, String joinPolicy, String status, LocalDateTime closeScheduledAt,
                             String closeReason, String ownerNickname, String myRole,
                             Set<BlogPermission> myPermissions, boolean subscribed, Long myPendingJoinRequestId,
                             boolean canDelegate, boolean shareable, String backgroundImageUrl) {}

    public record BlogPage(List<BlogSummary> items, int page, int size, long totalCount) {}

    public record MyBlogItem(Long id, String name, String address, String role, String status,
                             LocalDateTime closeScheduledAt) {}

    public record MyBlogs(List<MyBlogItem> owned, List<MyBlogItem> joined) {}
}
