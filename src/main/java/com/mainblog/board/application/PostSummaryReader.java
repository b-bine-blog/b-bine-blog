package com.mainblog.board.application;

import com.mainblog.board.application.BoardViews.BlogRef;
import com.mainblog.board.application.BoardViews.PostPage;
import com.mainblog.board.application.BoardViews.PostSummary;
import com.mainblog.common.markdown.MarkdownRenderer;
import com.mainblog.common.markdown.MarkdownText;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 글 목록 한 줄(PostSummary) 읽기 (004 T025). 블로그 목록·피드·검색·태그·공지 목록이 같이 쓴다.
 * 첫 사진은 저장된 본문을 걸러 처음 나오는 이미지로 정한다(D-100 #7). 별칭: posts p, users u, blogs b, categories c.
 */
@Component
public class PostSummaryReader {

    public static final String FROM = " FROM posts p JOIN users u ON u.id = p.author_id "
            + "LEFT JOIN blogs b ON b.id = p.blog_id LEFT JOIN categories c ON c.id = p.category_id ";

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
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + FROM + "WHERE " + where, Long.class, args.toArray());
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
        return jdbc.query(SELECT + FROM + "WHERE " + where + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                this::map, all.toArray());
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
                rs.getTimestamp("edited_at") != null);
    }
}
