package com.mainblog.blog.application;

import com.mainblog.blog.application.BlogViews.BlogDetail;
import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogMember;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.blog.domain.Visibility;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.storage.FileStorage;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 블로그 상세 응답 만들기 (T025, FR-005). 볼 수 있는지와 역할은 BlogPermissionService가 확인한다. */
@Service
public class BlogViewService {

    private final BlogRepository blogRepository;
    private final BlogPermissionService permissionService;
    private final BlogTagStore tagStore;
    private final FileStorage fileStorage;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public BlogViewService(BlogRepository blogRepository, BlogPermissionService permissionService,
                           BlogTagStore tagStore, FileStorage fileStorage, JdbcTemplate jdbc) {
        this.blogRepository = blogRepository;
        this.permissionService = permissionService;
        this.tagStore = tagStore;
        this.fileStorage = fileStorage;
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Transactional(readOnly = true)
    public BlogDetail byAddress(String address) {
        Blog blog = blogRepository.findByAddress(address)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
        return detail(permissionService.context(blog, BlogPermissionService.currentUserId()));
    }

    @Transactional(readOnly = true)
    public BlogDetail byId(long blogId) {
        return detail(permissionService.context(blogId));
    }

    public BlogDetail detail(BlogContext ctx) {
        Blog b = ctx.blog();
        BlogMember m = ctx.member();
        Long userId = ctx.userId();
        boolean subscribed = userId != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM blog_subscriptions WHERE blog_id = ? AND user_id = ?",
                Boolean.class, b.getId(), userId));
        Long pending = userId == null ? null : jdbc.query("SELECT id FROM blog_join_requests "
                        + "WHERE blog_id = ? AND user_id = ? AND status = 'PENDING' ORDER BY id DESC LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null, b.getId(), userId);
        String ownerNickname = jdbc.query("SELECT u.nickname FROM blog_members m JOIN users u ON u.id = m.user_id "
                        + "WHERE m.blog_id = ? AND m.role = 'OWNER'",
                rs -> rs.next() ? rs.getString(1) : null, b.getId());
        boolean owner = m != null && m.isOwner();
        return new BlogDetail(b.getId(), b.getName(), b.getAddress(), b.getDescription(), coverUrl(b.getId()),
                tagStore.tagsOf(b.getId()), b.getMemberCount(), b.getCreatedAt(), b.getVisibility().name(),
                b.getJoinPolicy().name(), b.getStatus().name(), b.getCloseScheduledAt(),
                b.getCloseReason() == null ? null : b.getCloseReason().name(), ownerNickname,
                m == null ? null : m.getRole().name(), BlogPermissionService.permissionsOf(m), subscribed, pending,
                owner && b.getMemberCount() > 1 && !b.isClosing(), b.getVisibility() == Visibility.LINK);
    }

    public String coverUrl(long blogId) {
        return jdbc.query("SELECT stored_name FROM images WHERE owner_type = 'BLOG_COVER' AND owner_id = ? "
                        + "AND deleted_at IS NULL ORDER BY id DESC LIMIT 1",
                rs -> rs.next() ? fileStorage.url(rs.getString(1)) : null, blogId);
    }

    public Map<Long, String> coverUrls(Collection<Long> blogIds) {
        Map<Long, String> result = new HashMap<>();
        if (blogIds.isEmpty()) {
            return result;
        }
        named.query("SELECT owner_id, stored_name FROM images WHERE owner_type = 'BLOG_COVER' "
                        + "AND owner_id IN (:ids) AND deleted_at IS NULL ORDER BY id",
                new MapSqlParameterSource("ids", blogIds),
                rs -> {
                    result.put(rs.getLong(1), fileStorage.url(rs.getString(2)));
                });
        return result;
    }
}
