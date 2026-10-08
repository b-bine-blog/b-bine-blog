package com.mainblog.social.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mainblog.blog.application.BlogContext;
import com.mainblog.blog.application.BlogPermissionService;
import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.board.application.PostPolicy;
import com.mainblog.board.application.PostStore;
import com.mainblog.board.application.PostStore.PostRow;
import com.mainblog.common.config.SocialProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.social.application.SocialGuard.UserRow;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 접수 (003 T035, SOC-06, FR-022~FR-025, FR-027, D-85, D-93, D-102 #1·#14·#15, research R-14).
 * 접수처는 신고하는 순간의 blog_members로 정한다(SEC-07). 블로그 자체·블로그장(그가 쓴 글·댓글 포함)·메인 프로필 회원은 ADMIN,
 * 블로그 안의 회원·글·댓글은 BLOG. 같은 대상은 14일에 한 번이고, 신고자 users 행을 잠가 동시 요청도 한 건만 들어간다.
 * 신고당한 사람에게는 알리지 않는다. 처리는 002(BLOG)·005(ADMIN)가 한다.
 */
@Service
public class ReportService {

    private static final List<String> TYPES = List.of("USER", "BLOG", "POST", "COMMENT");

    private final JdbcTemplate jdbc;
    private final SocialGuard guard;
    private final ReportReasons reasons;
    private final BlogRepository blogRepository;
    private final BlogPermissionService blogAuth;
    private final PostStore postStore;
    private final PostPolicy postPolicy;
    private final SocialProperties props;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ReportService(JdbcTemplate jdbc, SocialGuard guard, ReportReasons reasons, BlogRepository blogRepository,
                         BlogPermissionService blogAuth, PostStore postStore, PostPolicy postPolicy,
                         SocialProperties props, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.reasons = reasons;
        this.blogRepository = blogRepository;
        this.blogAuth = blogAuth;
        this.postStore = postStore;
        this.postPolicy = postPolicy;
        this.props = props;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public record ReportRequest(String targetType, Long targetId, Long blogId, String reason, String detail) {}

    public record Accepted(long id, String route) {}

    /** 확인을 마친 대상: 접수처, 대상 블로그(메인 프로필 신고는 null), 당시 내용. */
    private record Target(String route, Long blogId, Map<String, Object> snapshot) {}

    @Transactional
    public Accepted report(long reporterId, ReportRequest req) {
        guard.requireActor(reporterId);
        String type = req.targetType();
        if (type == null || !TYPES.contains(type)) {
            throw new InvalidInputException("targetType", "Pattern");
        }
        if (req.targetId() == null) {
            throw new InvalidInputException("targetId", "NotNull");
        }
        if (!reasons.exists(req.reason())) {
            throw new InvalidInputException("reason", "Pattern");
        }
        String detail = req.detail() == null ? "" : req.detail().strip();
        if (detail.codePointCount(0, detail.length()) > reasons.detailMaxLength()) {
            throw new InvalidInputException("detail", "Size");
        }
        if (detail.isEmpty() && reasons.detailRequired(req.reason())) {
            throw new InvalidInputException("detail", "NotBlank");
        }

        Target target = switch (type) {
            case "BLOG" -> blog(reporterId, req.targetId());
            case "POST" -> post(reporterId, req.targetId());
            case "COMMENT" -> comment(reporterId, req.targetId());
            default -> req.blogId() == null ? mainUser(reporterId, req.targetId())
                    : blogUser(reporterId, req.targetId(), req.blogId());
        };

        // 같은 신고자의 요청을 차례로 처리한다(유니크가 없어 행 잠금으로 2주 제한을 지킨다, SC-008).
        // 잠근 뒤의 조회도 잠금 읽기로 해야 앞 요청이 막 넣은 행이 보인다(REPEATABLE READ 스냅숏을 피함).
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, reporterId);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime last = jdbc.queryForObject("SELECT MAX(created_at) FROM reports WHERE reporter_id = ? "
                + "AND target_type = ? AND target_id = ? FOR SHARE", LocalDateTime.class, reporterId, type, req.targetId());
        if (last != null) {
            LocalDateTime reportableAt = last.plus(props.report().repeatAfter());
            if (now.isBefore(reportableAt)) {
                throw new BusinessException(ErrorCode.REPORT_TOO_SOON).with("reportableAt", reportableAt);
            }
        }
        long id = insert(target, reporterId, type, req.targetId(), req.reason(), detail.isEmpty() ? null : detail, now);
        return new Accepted(id, target.route());
    }

    private Target blog(long reporterId, long blogId) {
        BlogContext ctx = blogContext(blogId, reporterId);
        if (ctx.member() != null && ctx.member().isOwner()) {
            throw new BusinessException(ErrorCode.SELF_NOT_ALLOWED);
        }
        Blog b = ctx.blog();
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("name", b.getName());
        snap.put("address", b.getAddress());
        snap.put("description", b.getDescription());
        return new Target("ADMIN", blogId, snap);
    }

    private Target post(long reporterId, long postId) {
        PostRow post = postStore.find(postId).orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        postPolicy.requireReadable(post, reporterId);
        if (post.isNotice()) {
            throw new InvalidInputException("targetId", "NotAllowed"); // 공지 글은 신고 대상이 아니다(research 가정 5)
        }
        if (post.authorId() == reporterId) {
            throw new BusinessException(ErrorCode.SELF_NOT_ALLOWED);
        }
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("title", post.title());
        snap.put("content", post.content());
        snap.put("authorNickname", nickname(post.authorId()));
        snap.put("blogId", post.blogId());
        return new Target(isOwner(post.blogId(), post.authorId()) ? "ADMIN" : "BLOG", post.blogId(), snap);
    }

    private Target comment(long reporterId, long commentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT post_id, author_id, content FROM comments "
                + "WHERE id = ? AND status = 'VISIBLE'", commentId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        Map<String, Object> c = rows.get(0);
        long postId = ((Number) c.get("post_id")).longValue();
        long authorId = ((Number) c.get("author_id")).longValue();
        PostRow post = postStore.find(postId).orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        try {
            postPolicy.requireReadable(post, reporterId);
        } catch (BusinessException e) {
            if (e.errorCode() == ErrorCode.POST_NOT_FOUND) {
                throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
            }
            throw e;
        }
        if (post.isNotice()) {
            throw new InvalidInputException("targetId", "NotAllowed");
        }
        if (authorId == reporterId) {
            throw new BusinessException(ErrorCode.SELF_NOT_ALLOWED);
        }
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("content", c.get("content"));
        snap.put("authorNickname", nickname(authorId));
        snap.put("postId", postId);
        return new Target(isOwner(post.blogId(), authorId) ? "ADMIN" : "BLOG", post.blogId(), snap);
    }

    private Target mainUser(long reporterId, long userId) {
        UserRow user = guard.requireTarget(userId);
        if (userId == reporterId) {
            throw new BusinessException(ErrorCode.SELF_NOT_ALLOWED);
        }
        return new Target("ADMIN", null, userSnapshot(user));
    }

    /** 블로그 안 회원 신고: 대상은 그 블로그의 멤버만(research 가정 5). 블로그장이면 ADMIN. */
    private Target blogUser(long reporterId, long userId, long blogId) {
        blogContext(blogId, reporterId);
        UserRow user = guard.requireTarget(userId);
        String role = jdbc.query("SELECT role FROM blog_members WHERE blog_id = ? AND user_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, blogId, userId);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (userId == reporterId) {
            throw new BusinessException(ErrorCode.SELF_NOT_ALLOWED);
        }
        return new Target("OWNER".equals(role) ? "ADMIN" : "BLOG", blogId, userSnapshot(user));
    }

    private BlogContext blogContext(long blogId, long userId) {
        Blog blog = blogRepository.findById(blogId).orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
        return blogAuth.context(blog, userId);
    }

    private static Map<String, Object> userSnapshot(UserRow user) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("nickname", user.nickname());
        snap.put("bio", user.bio());
        return snap;
    }

    private boolean isOwner(Long blogId, long userId) {
        if (blogId == null) {
            return false;
        }
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM blog_members WHERE blog_id = ? AND user_id = ? "
                + "AND role = 'OWNER'", Integer.class, blogId, userId);
        return n != null && n > 0;
    }

    private String nickname(long userId) {
        return jdbc.query("SELECT nickname FROM users WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null, userId);
    }

    private long insert(Target t, long reporterId, String type, long targetId, String reason, String detail,
                        LocalDateTime now) {
        String snapshot;
        try {
            snapshot = objectMapper.writeValueAsString(t.snapshot());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO reports (blog_id, reporter_id, target_type, "
                    + "target_id, target_snapshot, reason, reason_detail, route, status, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, t.blogId());
            ps.setLong(2, reporterId);
            ps.setString(3, type);
            ps.setLong(4, targetId);
            ps.setString(5, snapshot);
            ps.setString(6, reason);
            ps.setString(7, detail);
            ps.setString(8, t.route());
            ps.setTimestamp(9, Timestamp.valueOf(now));
            ps.setTimestamp(10, Timestamp.valueOf(now));
            return ps;
        }, key);
        return key.getKey().longValue();
    }
}
