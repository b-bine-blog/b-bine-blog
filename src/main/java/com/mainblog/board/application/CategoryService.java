package com.mainblog.board.application;

import com.mainblog.blog.application.BlogContext;
import com.mainblog.blog.application.BlogPermission;
import com.mainblog.blog.application.BlogPermissionService;
import com.mainblog.board.application.BoardViews.CategoryList;
import com.mainblog.board.application.BoardViews.CategoryView;
import com.mainblog.common.config.BoardProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리 (004 T026, FR-021, FR-022, D-100 #8, research R-06).
 * 블로그당 30개, 이름 1~30자, 블로그 안 중복 불가(블로그 행을 잠그고 확인). 지우면 그 글은 미분류로 옮긴다.
 * 관리는 POST_MANAGE 권한자만(블로그장, 글 관리 권한 부블로그장).
 */
@Service
public class CategoryService {

    private final JdbcTemplate jdbc;
    private final BlogPermissionService blogAuth;
    private final PostPolicy policy;
    private final BoardProperties props;
    private final Clock clock;

    public CategoryService(JdbcTemplate jdbc, BlogPermissionService blogAuth, PostPolicy policy,
                           BoardProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.blogAuth = blogAuth;
        this.policy = policy;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CategoryList list(long blogId, Long userId) {
        BlogContext ctx = blogAuth.context(blogId);
        List<CategoryView> categories = jdbc.query("SELECT c.id, c.name, c.sort_order, "
                        + "(SELECT COUNT(*) FROM posts p WHERE p.category_id = c.id AND p.status = 'VISIBLE') AS cnt "
                        + "FROM categories c WHERE c.blog_id = ? ORDER BY c.sort_order, c.id",
                (rs, i) -> new CategoryView(rs.getLong("id"), rs.getString("name"), rs.getInt("sort_order"),
                        rs.getInt("cnt")), blogId);
        Integer uncategorized = jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE blog_id = ? "
                + "AND board_type = 'BLOG' AND status = 'VISIBLE' AND category_id IS NULL", Integer.class, blogId);
        boolean canManage = BlogPermissionService.allows(ctx.member(), BlogPermission.POST_MANAGE)
                && !policy.isAdmin(userId);
        return new CategoryList(categories, uncategorized == null ? 0 : uncategorized, canManage);
    }

    @Transactional
    public CategoryView create(long blogId, String rawName) {
        lockAndCheck(blogId);
        String name = validName(rawName);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE blog_id = ?", Integer.class, blogId);
        if (count != null && count >= props.category().maxCount()) {
            throw new BusinessException(ErrorCode.CATEGORY_LIMIT_EXCEEDED);
        }
        requireUnique(blogId, name, null);
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM categories WHERE blog_id = ?",
                Integer.class, blogId);
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("INSERT INTO categories (blog_id, name, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                blogId, name, next, now, now);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return new CategoryView(id, name, next == null ? 1 : next, 0);
    }

    @Transactional
    public CategoryView rename(long blogId, long categoryId, String rawName) {
        lockAndCheck(blogId);
        requireExists(blogId, categoryId);
        String name = validName(rawName);
        requireUnique(blogId, name, categoryId);
        jdbc.update("UPDATE categories SET name = ?, updated_at = ? WHERE id = ?", name, LocalDateTime.now(clock),
                categoryId);
        return jdbc.queryForObject("SELECT c.id, c.name, c.sort_order, "
                        + "(SELECT COUNT(*) FROM posts p WHERE p.category_id = c.id AND p.status = 'VISIBLE') AS cnt "
                        + "FROM categories c WHERE c.id = ?",
                (rs, i) -> new CategoryView(rs.getLong("id"), rs.getString("name"), rs.getInt("sort_order"),
                        rs.getInt("cnt")), categoryId);
    }

    /** 지우면 그 카테고리의 글(삭제된 글 포함)은 미분류가 된다(D-100 #8). */
    @Transactional
    public void delete(long blogId, long categoryId) {
        lockAndCheck(blogId);
        requireExists(blogId, categoryId);
        jdbc.update("UPDATE posts SET category_id = NULL WHERE category_id = ?", categoryId);
        jdbc.update("DELETE FROM categories WHERE id = ?", categoryId);
    }

    /** categoryIds는 그 블로그의 카테고리 전부를 원하는 순서로. 빠지거나 남의 것이 섞이면 400. */
    @Transactional
    public void reorder(long blogId, List<Long> categoryIds) {
        lockAndCheck(blogId);
        List<Long> existing = jdbc.queryForList("SELECT id FROM categories WHERE blog_id = ?", Long.class, blogId);
        if (categoryIds == null || categoryIds.size() != existing.size()
                || !new HashSet<>(categoryIds).equals(new HashSet<>(existing))) {
            throw new InvalidInputException("categoryIds", "Mismatch");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        for (int i = 0; i < categoryIds.size(); i++) {
            jdbc.update("UPDATE categories SET sort_order = ?, updated_at = ? WHERE id = ?", i + 1, now,
                    categoryIds.get(i));
        }
    }

    private void lockAndCheck(long blogId) {
        jdbc.query("SELECT id FROM blogs WHERE id = ? FOR UPDATE", rs -> null, blogId);
        blogAuth.require(blogId, BlogPermission.POST_MANAGE);
    }

    private void requireExists(long blogId, long categoryId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE id = ? AND blog_id = ?", Integer.class,
                categoryId, blogId);
        if (n == null || n == 0) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    private void requireUnique(long blogId, String name, Long exceptId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE blog_id = ? AND name = ? AND id <> ?",
                Integer.class, blogId, name, exceptId == null ? -1L : exceptId);
        if (n != null && n > 0) {
            throw new BusinessException(ErrorCode.CATEGORY_DUPLICATE);
        }
    }

    private String validName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty()) {
            throw new InvalidInputException("name", "NotBlank");
        }
        if (name.codePointCount(0, name.length()) > props.category().nameMaxLength()) {
            throw new InvalidInputException("name", "Size");
        }
        return name;
    }
}
