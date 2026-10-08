package com.mainblog.blog.application;

import com.mainblog.blog.application.BlogViews.BlogPage;
import com.mainblog.blog.application.BlogViews.BlogSummary;
import com.mainblog.blog.application.BlogViews.MyBlogItem;
import com.mainblog.blog.application.BlogViews.MyBlogs;
import com.mainblog.common.config.BlogProperties;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.tag.TagNormalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메인 블로그 목록·블로그 검색·내 블로그 (T031, BLG-02, BLG-03, BLG-06, FR-014~FR-016, research R-07).
 * 공개(PUBLIC)이고 숨기지 않은 운영 중·폐쇄 예정 블로그만 보인다. 일부 공개·비공개는 0건(SC-007).
 */
@Service
public class BlogQueryService {

    private static final Pattern HAS_WORD = Pattern.compile("[\\p{L}\\p{N}]");
    private static final String LISTED = "b.status IN ('ACTIVE', 'CLOSING') AND b.visibility = 'PUBLIC' "
            + "AND b.hidden = FALSE";

    private final JdbcTemplate jdbc;
    private final BlogTagStore tagStore;
    private final BlogViewService viewService;
    private final BlogProperties properties;

    public BlogQueryService(JdbcTemplate jdbc, BlogTagStore tagStore, BlogViewService viewService,
                            BlogProperties properties) {
        this.jdbc = jdbc;
        this.tagStore = tagStore;
        this.viewService = viewService;
        this.properties = properties;
    }

    public enum Sort {
        LATEST("b.created_at DESC, b.id DESC"),
        /** 멤버 수가 많은 순, 같으면 최신순(D-89). */
        POPULAR("b.member_count DESC, b.created_at DESC, b.id DESC");

        private final String orderBy;

        Sort(String orderBy) {
            this.orderBy = orderBy;
        }

        public static Sort parse(String raw) {
            if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("latest")) {
                return LATEST;
            }
            if (raw.equalsIgnoreCase("popular")) {
                return POPULAR;
            }
            throw new InvalidInputException("sort", "Pattern");
        }
    }

    @Transactional(readOnly = true)
    public BlogPage list(String sort, Integer page, Integer size) {
        int p = page(page);
        int s = size(size);
        return query(LISTED, List.of(), Sort.parse(sort), p, s);
    }

    /** 이름·소개(ngram 전문 검색)와 블로그 태그로 찾는다. 검색어는 2~20자, 글자·숫자가 하나 이상(6.1). */
    @Transactional(readOnly = true)
    public BlogPage search(String q, String sort, Integer page, Integer size) {
        String query = q == null ? "" : q.strip();
        int len = query.codePointCount(0, query.length());
        if (len < properties.search().minLength() || len > properties.search().maxLength()
                || !HAS_WORD.matcher(query).find()) {
            throw new InvalidInputException("q", "Size");
        }
        return searchAnySize(query, sort, page(page), size(size));
    }

    /** 검색어 확인을 마친 통합 검색(004)용. 페이지 크기 제한 없이 블로그 미리보기 개수를 받는다. */
    @Transactional(readOnly = true)
    public BlogPage searchAnySize(String query, String sort, int page, int size) {
        // 따옴표로 묶어 구절 검색으로 만든다. 불리언 연산자 문자가 해석되지 않게 한다.
        String phrase = "\"" + query.replace("\"", " ") + "\"";
        String tag = TagNormalizer.normalize(query, properties.tag().maxLength());
        String where = LISTED + " AND (MATCH(b.name, b.description) AGAINST (? IN BOOLEAN MODE)"
                + " OR b.id IN (SELECT bt.blog_id FROM blog_tags bt JOIN tags t ON t.id = bt.tag_id WHERE t.name = ?))";
        return query(where, List.of(phrase, tag == null ? "" : tag), Sort.parse(sort), page, size);
    }

    /** 구독한 블로그(폐쇄 제외, 숨김·일부 공개·비공개 포함). 구독 목록은 002에서 페이지 없이 준다(R-15). */
    @Transactional(readOnly = true)
    public List<BlogSummary> subscribedBlogs(long userId) {
        String where = "b.status IN ('ACTIVE', 'CLOSING') AND b.id IN "
                + "(SELECT s.blog_id FROM blog_subscriptions s WHERE s.user_id = ?)";
        return query(where, List.of(userId), Sort.LATEST, 1, Integer.MAX_VALUE).items();
    }

    private BlogPage query(String where, List<Object> params, Sort sort, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM blogs b WHERE " + where, Long.class,
                params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((page - 1) * size);
        List<Row> rows = jdbc.query("SELECT b.id, b.name, b.address, b.description, b.member_count, b.created_at "
                        + "FROM blogs b WHERE " + where + " ORDER BY " + sort.orderBy + " LIMIT ? OFFSET ?",
                (rs, i) -> new Row(rs.getLong("id"), rs.getString("name"), rs.getString("address"),
                        rs.getString("description"), rs.getInt("member_count"),
                        rs.getObject("created_at", LocalDateTime.class)),
                pageParams.toArray());
        List<Long> ids = rows.stream().map(Row::id).toList();
        Map<Long, List<String>> tags = tagStore.tagsOf(ids);
        Map<Long, String> covers = viewService.coverUrls(ids);
        List<BlogSummary> items = rows.stream()
                .map(r -> new BlogSummary(r.id(), r.name(), r.address(), r.description(), covers.get(r.id()),
                        tags.getOrDefault(r.id(), List.of()), r.memberCount(), r.createdAt()))
                .toList();
        return new BlogPage(items, page, size, total == null ? 0 : total);
    }

    /** 운영하는 블로그(지금 블로그장)와 참여한 블로그(부블로그장·멤버). 폐쇄된 블로그는 뺀다(FR-016). */
    @Transactional(readOnly = true)
    public MyBlogs myBlogs(long userId) {
        List<MyBlogItem> all = jdbc.query("""
                SELECT b.id, b.name, b.address, m.role, b.status, b.close_scheduled_at
                FROM blog_members m JOIN blogs b ON b.id = m.blog_id
                WHERE m.user_id = ? AND b.status IN ('ACTIVE', 'CLOSING')
                ORDER BY m.joined_at DESC, b.id DESC""",
                (rs, i) -> new MyBlogItem(rs.getLong("id"), rs.getString("name"), rs.getString("address"),
                        rs.getString("role"), rs.getString("status"),
                        rs.getObject("close_scheduled_at", LocalDateTime.class)),
                userId);
        return new MyBlogs(all.stream().filter(b -> "OWNER".equals(b.role())).toList(),
                all.stream().filter(b -> !"OWNER".equals(b.role())).toList());
    }

    public int page(Integer page) {
        if (page == null) {
            return 1;
        }
        if (page < 1) {
            throw new InvalidInputException("page", "Min");
        }
        return page;
    }

    /** 10·20·30 중 하나, 기본 10(FR-014). */
    public int size(Integer size) {
        if (size == null) {
            return properties.list().defaultSize();
        }
        if (!properties.list().pageSizes().contains(size)) {
            throw new InvalidInputException("size", "Pattern");
        }
        return size;
    }

    private record Row(Long id, String name, String address, String description, int memberCount,
                       LocalDateTime createdAt) {}
}
