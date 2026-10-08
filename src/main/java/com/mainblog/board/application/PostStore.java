package com.mainblog.board.application;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/**
 * posts 행 읽기·쓰기 (004 T008). 게시판 테이블은 엔티티 없이 JdbcTemplate로 다룬다.
 */
@Component
public class PostStore {

    public static final String BLOG = "BLOG";
    public static final String NOTICE = "NOTICE";

    private static final String COLUMNS = "id, blog_id, author_id, category_id, board_type, title, content, status, "
            + "author_left, view_count, like_count, created_at";

    private final JdbcTemplate jdbc;

    public PostStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record PostRow(long id, Long blogId, long authorId, Long categoryId, String boardType, String title,
                          String content, String status, boolean authorLeft, int viewCount, int likeCount,
                          LocalDateTime createdAt) {

        public boolean isNotice() {
            return NOTICE.equals(boardType);
        }

        public boolean isVisible() {
            return "VISIBLE".equals(status);
        }

        /** 메인 관리자가 숨긴 글(005 D-103 #10). */
        public boolean isHiddenByAdmin() {
            return "HIDDEN".equals(status);
        }
    }

    public Optional<PostRow> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM posts WHERE id = ?", PostStore::map, id).stream().findFirst();
    }

    /** 수정·삭제·좋아요처럼 같은 글을 동시에 바꿀 때 행을 잠근다. */
    public Optional<PostRow> findForUpdate(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM posts WHERE id = ? FOR UPDATE", PostStore::map, id)
                .stream().findFirst();
    }

    public long insert(Long blogId, long authorId, Long categoryId, String boardType, String title, String content,
                       LocalDateTime now) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO posts (blog_id, author_id, category_id, board_type, "
                    + "title, content, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 'VISIBLE', ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, blogId);
            ps.setLong(2, authorId);
            ps.setObject(3, categoryId);
            ps.setString(4, boardType);
            ps.setString(5, title);
            ps.setString(6, content);
            ps.setTimestamp(7, Timestamp.valueOf(now));
            ps.setTimestamp(8, Timestamp.valueOf(now));
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    public void update(long id, String title, String content, Long categoryId, LocalDateTime now) {
        jdbc.update("UPDATE posts SET title = ?, content = ?, category_id = ?, updated_at = ? WHERE id = ?",
                title, content, categoryId, now, id);
    }

    public void markDeleted(long id, long deletedBy, LocalDateTime now) {
        jdbc.update("UPDATE posts SET status = 'DELETED', deleted_at = ?, deleted_by = ?, updated_at = ? WHERE id = ?",
                now, deletedBy, now, id);
    }

    public List<String> tagsOf(long postId) {
        return jdbc.queryForList("SELECT t.name FROM post_tags pt JOIN tags t ON t.id = pt.tag_id "
                + "WHERE pt.post_id = ? ORDER BY pt.id", String.class, postId);
    }

    private static PostRow map(ResultSet rs, int i) throws SQLException {
        return new PostRow(rs.getLong("id"), (Long) rs.getObject("blog_id", Long.class), rs.getLong("author_id"),
                rs.getObject("category_id", Long.class), rs.getString("board_type"), rs.getString("title"),
                rs.getString("content"), rs.getString("status"), rs.getBoolean("author_left"),
                rs.getInt("view_count"), rs.getInt("like_count"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }
}
