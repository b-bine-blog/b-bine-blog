package com.mainblog.board.application;

import com.mainblog.blog.application.BlogQueryService;
import com.mainblog.blog.application.BlogViews.BlogPage;
import com.mainblog.board.application.BoardViews.BlogSearchItem;
import com.mainblog.board.application.BoardViews.BlogSearchPage;
import com.mainblog.board.application.BoardViews.PostPage;
import com.mainblog.board.application.BoardViews.SearchResult;
import com.mainblog.common.config.BoardProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통합 검색 (004 T043, FR-052~FR-058, D-89, D-100 #16·#17, research R-13).
 * 공개·숨기지 않은 블로그와 그 보이는 글만 찾고, 보는 회원이 차단한 작성자의 글은 결과와 개수에서 뺀다.
 * ALL은 블로그 앞 5개(전체 개수)와 글 번호 페이지를 함께 준다.
 */
@Service
public class SearchService {

    public enum Type { ALL, BLOG, TITLE, TAG, NICKNAME }

    public enum Sort { RELEVANCE, LATEST, POPULAR }

    private static final Pattern HAS_WORD = Pattern.compile("[\\p{L}\\p{N}]");
    static final String TITLE_MATCH = "MATCH(p.title) AGAINST (? IN BOOLEAN MODE)";
    static final String TAG_MATCH = "EXISTS (SELECT 1 FROM post_tags pt JOIN tags t ON t.id = pt.tag_id "
            + "WHERE pt.post_id = p.id AND t.name = ?)";
    static final String NICKNAME_MATCH = "(u.nickname = ? AND u.status = 'ACTIVE' AND p.author_left = FALSE)";

    private final PostSummaryReader reader;
    private final BlogQueryService blogQueryService;
    private final RecentSearchService recentSearches;
    private final PostTagService tagService;
    private final BoardPaging paging;
    private final BoardProperties props;

    public SearchService(PostSummaryReader reader, BlogQueryService blogQueryService,
                         RecentSearchService recentSearches, PostTagService tagService, BoardPaging paging,
                         BoardProperties props) {
        this.reader = reader;
        this.blogQueryService = blogQueryService;
        this.recentSearches = recentSearches;
        this.tagService = tagService;
        this.paging = paging;
        this.props = props;
    }

    @Transactional
    public SearchResult search(String q, String rawType, String rawSort, Integer page, Integer size, Long viewerId) {
        String keyword = validKeyword(q);
        Type type = parse(Type.class, rawType, Type.ALL, "type");
        Sort sort = parse(Sort.class, rawSort, Sort.RELEVANCE, "sort");
        int p = paging.page(page);
        int s = paging.size(size);
        if (viewerId != null) {
            recentSearches.remember(viewerId, keyword);
        }
        return switch (type) {
            case ALL -> new SearchResult(type.name(), blogs(keyword, sort, 1, props.search().blogPreviewSize()),
                    posts(type, keyword, sort, p, s, viewerId));
            case BLOG -> new SearchResult(type.name(), blogs(keyword, sort, p, s), null);
            default -> new SearchResult(type.name(), null, posts(type, keyword, sort, p, s, viewerId));
        };
    }

    /** 제목 FULLTEXT는 띄어쓰기를 포함한 구절 그대로 찾는다. */
    static String phrase(String keyword) {
        return "\"" + keyword.replace("\"", " ") + "\"";
    }

    private PostPage posts(Type type, String keyword, Sort sort, int page, int size, Long viewerId) {
        String phrase = phrase(keyword);
        String tag = tagService.normalizeOne(keyword);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(PostVisibility.PUBLIC_BLOG_POST).append(" AND ");
        switch (type) {
            case TITLE -> {
                where.append(TITLE_MATCH);
                args.add(phrase);
            }
            case TAG -> {
                where.append(TAG_MATCH);
                args.add(tag == null ? "" : tag);
            }
            case NICKNAME -> {
                where.append(NICKNAME_MATCH);
                args.add(keyword);
            }
            default -> {
                where.append("(").append(TITLE_MATCH).append(" OR ").append(TAG_MATCH).append(" OR ")
                        .append(NICKNAME_MATCH).append(")");
                args.add(phrase);
                args.add(tag == null ? "" : tag);
                args.add(keyword);
            }
        }
        where.append(PostVisibility.notBlocked(viewerId, args));
        String orderBy;
        List<Object> orderArgs = new ArrayList<>();
        switch (sort) {
            case LATEST -> orderBy = "p.created_at DESC, p.id DESC";
            case POPULAR -> orderBy = "p.like_count DESC, p.created_at DESC, p.id DESC";
            default -> {
                // 맞은 조건 수(제목·태그·닉네임) 많은 순, 같으면 최신순.
                // MATCH 점수(DOUBLE)를 정수와 더하면 MySQL이 범위 오류를 내서 일치 여부(0·1)로 더한다.
                orderBy = "((" + TITLE_MATCH + ") > 0) + (" + TAG_MATCH + ") + (" + NICKNAME_MATCH + ") DESC, "
                        + "p.created_at DESC, p.id DESC";
                orderArgs.add(phrase);
                orderArgs.add(tag == null ? "" : tag);
                orderArgs.add(keyword);
            }
        }
        return reader.page(where.toString(), args, orderBy, orderArgs, page, size);
    }

    /** 블로그 검색은 002와 같은 규칙(이름·소개 ngram, 블로그 태그). 관련도는 최신순으로 둔다. */
    private BlogSearchPage blogs(String keyword, Sort sort, int page, int size) {
        String blogSort = sort == Sort.POPULAR ? "popular" : "latest";
        BlogPage result = blogQueryService.searchAnySize(keyword, blogSort, page, size);
        List<BlogSearchItem> items = result.items().stream()
                .map(b -> new BlogSearchItem(b.id(), b.address(), b.name(), b.description(), b.coverImageUrl(),
                        b.memberCount()))
                .toList();
        return new BlogSearchPage(items, result.totalCount(), (int) ((result.totalCount() + size - 1) / size));
    }

    /** 앞뒤 공백을 뺀 2~20자, 글자·숫자가 하나 이상. */
    String validKeyword(String q) {
        String keyword = q == null ? "" : q.strip();
        int len = keyword.codePointCount(0, keyword.length());
        if (len < props.search().minLength() || len > props.search().maxLength() || !HAS_WORD.matcher(keyword).find()) {
            throw new BusinessException(ErrorCode.SEARCH_KEYWORD_INVALID);
        }
        return keyword;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, E fallback, String field) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(field, "Pattern");
        }
    }
}
