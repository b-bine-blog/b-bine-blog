package com.mainblog.board.application;

import java.time.LocalDateTime;
import java.util.List;

/** 게시판 응답 모양 (contracts/board-api.yaml components.schemas). 개인정보는 넣지 않는다(FR-073, D-22). */
public final class BoardViews {

    private BoardViews() {
    }

    /** 작성자. 닉네임·프로필 사진만. state: ACTIVE, LEFT_BLOG("탈퇴한 계정"), WITHDRAWN("탈퇴한 회원"). */
    public record Author(Long id, String nickname, String profileImageUrl, String state) {}

    /** 글이 속한 블로그. 공지면 null. */
    public record BlogRef(long id, String address, String name, String visibility) {}

    public record PostSummary(long id, String boardType, BlogRef blog, String title, Author author,
                              String categoryName, String thumbnailUrl, int likeCount, int viewCount,
                              int commentCount, LocalDateTime createdAt) {}

    public record PostDetail(long id, String boardType, BlogRef blog, String title, Author author,
                             String categoryName, String thumbnailUrl, int likeCount, int viewCount,
                             int commentCount, LocalDateTime createdAt,
                             String content, String contentHtml, Long categoryId, List<String> tags, boolean liked,
                             boolean canEdit, boolean canDelete, boolean canComment, boolean shareable,
                             boolean hiddenByAdmin) {}

    public record PostPage(List<PostSummary> items, int page, int size, long totalElements, int totalPages) {

        public static PostPage of(List<PostSummary> items, int page, int size, long total) {
            return new PostPage(items, page, size, total, (int) ((total + size - 1) / size));
        }
    }

    public record CategoryView(long id, String name, int sortOrder, int postCount) {}

    public record CategoryList(List<CategoryView> categories, int uncategorizedCount, boolean canManage) {}

    public record UploadedImage(long id, String url) {}

    public record UploadedImages(List<UploadedImage> images) {}

    /** hidden: 메인 관리자가 숨긴 최상위 댓글 자리(답글이 남았을 때만, 내용·작성자 없음, 005 T042). */
    public record CommentView(long id, Long parentId, boolean deleted, String content, Author author,
                              String replyToNickname, boolean edited, boolean canEdit, boolean canDelete,
                              LocalDateTime createdAt, List<CommentView> replies, boolean hidden) {}

    public record CommentPage(List<CommentView> items, int page, int totalPages, long totalElements) {}

    public record LikeResult(boolean liked, int likeCount) {}

    public record BlogSearchItem(long id, String address, String name, String description, String coverImageUrl,
                                 int memberCount) {}

    public record BlogSearchPage(List<BlogSearchItem> items, long totalElements, int totalPages) {}

    public record SearchResult(String type, BlogSearchPage blogs, PostPage posts) {}

    public record RecentSearch(long id, String keyword, LocalDateTime searchedAt) {}

    public record FeedResult(String tab, List<PostSummary> items, Integer page, Integer totalPages,
                             String nextCursor) {}
}
