package com.mainblog.board.application;

import com.mainblog.blog.application.BlogPermissionService;
import com.mainblog.board.application.BoardViews.PostPage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 글 목록·태그 글 목록·공지 목록 (004 T025·T044·T052, FR-017~FR-021, FR-023, FR-065).
 * 최신순 번호 페이지(10·20·30). 블로그 안 목록은 002 블로그 열람 규칙을 먼저 확인한다.
 */
@Service
public class PostListService {

    private static final String LATEST = "p.created_at DESC, p.id DESC";

    private final BlogPermissionService blogAuth;
    private final PostSummaryReader reader;
    private final BoardPaging paging;
    private final PostTagService tagService;
    private final SearchService searchService;

    public PostListService(BlogPermissionService blogAuth, PostSummaryReader reader, BoardPaging paging,
                           PostTagService tagService, SearchService searchService) {
        this.blogAuth = blogAuth;
        this.reader = reader;
        this.paging = paging;
        this.tagService = tagService;
        this.searchService = searchService;
    }

    /** categoryId: null이면 전체, 0이면 미분류. */
    @Transactional(readOnly = true)
    public PostPage blogPosts(long blogId, Long categoryId, Integer page, Integer size) {
        return blogPosts(blogId, categoryId, null, page, size);
    }

    /**
     * q가 있으면 이 블로그 글 안에서 제목·태그·작성자 닉네임으로 찾는다(블로그 안 검색, D-136).
     * 검색어 규칙은 통합 검색과 같다(BRD-08). 최근 검색어에는 남기지 않는다.
     */
    @Transactional(readOnly = true)
    public PostPage blogPosts(long blogId, Long categoryId, String q, Integer page, Integer size) {
        int p = paging.page(page);
        int s = paging.size(size);
        blogAuth.context(blogId);
        List<Object> args = new ArrayList<>(List.of(blogId));
        String where = PostVisibility.BLOG_POST;
        if (categoryId != null) {
            if (categoryId == 0) {
                where += " AND p.category_id IS NULL";
            } else {
                where += " AND p.category_id = ?";
                args.add(categoryId);
            }
        }
        if (q != null && !q.isBlank()) {
            String keyword = searchService.validKeyword(q);
            String tag = tagService.normalizeOne(keyword);
            where += " AND (" + SearchService.TITLE_MATCH + " OR " + SearchService.TAG_MATCH + " OR "
                    + SearchService.NICKNAME_MATCH + ")";
            args.add(SearchService.phrase(keyword));
            args.add(tag == null ? "" : tag);
            args.add(keyword);
        }
        // 내가 차단한 회원의 글은 빼고, 둘 다 이 블로그 멤버면 보인다(003 T043, FR-017, FR-018, D-102 #11)
        where += PostVisibility.notBlocked(BlogPermissionService.currentUserId(), args);
        return reader.page(where, args, LATEST, p, s);
    }

    /** 태그 정리 규칙을 거친 이름이 정확히 같은 공개 글. 규칙에 맞지 않는 이름이면 400. */
    @Transactional(readOnly = true)
    public PostPage tagPosts(String rawName, Long viewerId, Integer page, Integer size) {
        int p = paging.page(page);
        int s = paging.size(size);
        String tag = tagService.normalizeOne(rawName);
        if (tag == null) {
            throw new com.mainblog.common.error.InvalidInputException("name", "Pattern");
        }
        List<Object> args = new ArrayList<>();
        args.add(tag);
        String where = PostVisibility.PUBLIC_BLOG_POST + " AND EXISTS (SELECT 1 FROM post_tags pt JOIN tags t "
                + "ON t.id = pt.tag_id WHERE pt.post_id = p.id AND t.name = ?)" + PostVisibility.notBlocked(viewerId, args);
        return reader.page(where, args, LATEST, p, s);
    }

    @Transactional(readOnly = true)
    public PostPage notices(Integer page, Integer size) {
        return reader.page(PostVisibility.NOTICE_POST, List.of(), LATEST, paging.page(page), paging.size(size));
    }
}
