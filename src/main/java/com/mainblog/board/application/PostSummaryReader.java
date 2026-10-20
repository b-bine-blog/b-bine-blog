package com.mainblog.board.application;

import com.mainblog.blog.application.BlogPermissionService;
import com.mainblog.board.application.BoardViews.BlogRef;
import com.mainblog.board.application.BoardViews.PostPage;
import com.mainblog.board.application.BoardViews.PostSummary;
import com.mainblog.common.markdown.MarkdownRenderer;
import com.mainblog.common.markdown.MarkdownText;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 글 목록 한 줄(PostSummary) 읽기 (004 T025). 블로그 목록·피드·검색·태그·공지 목록이 같이 쓴다.
 * 첫 사진은 저장된 본문을 걸러 처음 나오는 이미지로 정한다(D-100 #7). 별칭: posts p, users u, blogs b, categories c.
 * 로그인한 회원이 보면 그 회원이 스크랩한 글인지(scrapped)도 함께 읽는다(D-134). 비회원은 늘 false.
 * 한 페이지는 글 번호부터 찾고, 본문·댓글 수는 그 번호의 글에만 읽는다(D-142). 조건과 정렬식은 p·b 별칭만 쓴다.
 */
@Component
public class PostSummaryReader {

    public static final String FROM = " FROM posts p JOIN users u ON u.id = p.author_id "
            + "LEFT JOIN blogs b ON b.id = p.blog_id LEFT JOIN categories c ON c.id = p.category_id ";

    /**
     * 번호와 개수를 찾을 때의 FROM. 조건은 p·b만 쓰므로 작성자·카테고리는 잇지 않는다(작성자는 늘 있다).
     * JOIN_ORDER(p, b): 글 인덱스 순서대로 읽고 블로그는 번호로 찾는다. 없으면 MySQL이 블로그부터 읽어
     * 글 전체를 본문째 정렬해 글 30만 건에서 메인이 17초 걸렸다(D-142). 태그·구독 같은 하위 쿼리 순서는 MySQL에 맡긴다.
     */
    private static final String ID_SELECT = "SELECT /*+ JOIN_ORDER(p, b) */ ";
    private static final String ID_FROM = " FROM posts p LEFT JOIN blogs b ON b.id = p.blog_id ";

    private static final String SELECT = "SELECT p.id, p.board_type, p.blog_id, b.address, b.name AS blog_name, "
            + "b.visibility, p.title, p.content, p.author_id, p.author_left, u.nickname, u.status AS user_status, "
            + AuthorView.PROFILE_NAME_SQL + " AS profile_name, c.name AS category_name, p.like_count, p.view_count, "
            + "(SELECT COUNT(*) FROM comments cm WHERE cm.post_id = p.id AND cm.status = 'VISIBLE') AS comment_count, "
            + "p.created_at, p.edited_at";

    private final JdbcTemplate jdbc;
    private final MarkdownRenderer renderer;
    private final AuthorView authorView;

    public PostSummaryReader(JdbcTemplate jdbc, MarkdownRenderer renderer, AuthorView authorView) {
        this.jdbc = jdbc;
        this.renderer = renderer;
        this.authorView = authorView;
    }

    /** where(“WHERE ...” 없이 조건만)와 정렬로 한 페이지를 읽는다. page는 1부터. */
    public PostPage page(String where, List<Object> args, String orderBy, int page, int size) {
        return page(where, args, orderBy, List.of(), page, size);
    }

    /** orderArgs는 정렬식(관련도 점수 등)에 쓰는 바인딩 값. */
    public PostPage page(String where, List<Object> args, String orderBy, List<Object> orderArgs, int page, int size) {
        Long total = jdbc.queryForObject(ID_SELECT + "COUNT(*)" + ID_FROM + "WHERE " + where, Long.class,
                args.toArray());
        List<PostSummary> items = list(where, args, orderBy, orderArgs, size, (long) (page - 1) * size);
        return PostPage.of(items, page, size, total == null ? 0 : total);
    }

    public List<PostSummary> list(String where, List<Object> args, String orderBy, int limit, long offset) {
        return list(where, args, orderBy, List.of(), limit, offset);
    }

    public List<PostSummary> list(String where, List<Object> args, String orderBy, List<Object> orderArgs, int limit,
                                  long offset) {
        List<Object> all = new ArrayList<>(args);
        all.addAll(orderArgs);
        all.add(limit);
        all.add(offset);
        List<Long> ids = jdbc.queryForList(ID_SELECT + "p.id" + ID_FROM + "WHERE " + where + " ORDER BY " + orderBy
                + " LIMIT ? OFFSET ?", Long.class, all.toArray());
        return byIds(ids);
    }

    /** 번호 순서 그대로 한 줄씩 읽는다. */
    private List<PostSummary> byIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Object> all = new ArrayList<>(ids);
        Long viewerId = BlogPermissionService.currentUserId();
        String scrapped = ", FALSE AS scrapped";
        if (viewerId != null) {
            scrapped = ", EXISTS (SELECT 1 FROM post_scraps ps WHERE ps.post_id = p.id AND ps.user_id = ?) AS scrapped";
            all.add(0, viewerId);
        }
        String in = String.join(", ", Collections.nCopies(ids.size(), "?"));
        List<PostSummary> rows = jdbc.query(SELECT + scrapped + FROM + "WHERE p.id IN (" + in + ")", this::map,
                all.toArray());
        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            order.put(ids.get(i), i);
        }
        return rows.stream().sorted(Comparator.comparing(r -> order.get(r.id()))).toList();
    }

    private PostSummary map(ResultSet rs, int i) throws SQLException {
        Long blogId = rs.getObject("blog_id", Long.class);
        BlogRef blog = blogId == null ? null
                : new BlogRef(blogId, rs.getString("address"), rs.getString("blog_name"), rs.getString("visibility"));
        String html = renderer.toSafeHtml(rs.getString("content"));
        return new PostSummary(rs.getLong("id"), rs.getString("board_type"), blog, rs.getString("title"),
                authorView.ofPost(rs.getLong("author_id"), rs.getString("nickname"), rs.getString("user_status"),
                        rs.getString("profile_name"), rs.getBoolean("author_left")),
                rs.getString("category_name"), MarkdownText.firstImage(html), rs.getInt("like_count"),
                rs.getInt("view_count"), rs.getInt("comment_count"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("edited_at") != null, rs.getBoolean("scrapped"));
    }
}
