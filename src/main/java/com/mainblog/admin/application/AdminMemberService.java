package com.mainblog.admin.application;

import com.mainblog.admin.application.AdminViews.Page;
import com.mainblog.admin.application.AdminViews.UserRef;
import com.mainblog.common.config.AdminProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.masking.EmailMasker;
import com.mainblog.common.masking.PhoneMasker;
import com.mainblog.common.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 회원 조회와 "전체 보기" (005 T015, ADM-01, FR-005~FR-009, 4.4, D-22, D-103 #2~#5, research R-04).
 * 이메일·전화번호는 서버가 응답을 만들 때 가린다. 원문은 "전체 보기" 응답에만 있고, 그때마다 같은 트랜잭션에서 PRIVACY_VIEW를 남긴다.
 * 비밀번호는 어느 응답에도 없다(FR-009). 원문을 로그에 쓰지 않는다.
 */
@Service
public class AdminMemberService {

    public record MemberSummary(long id, String nickname, String name, String maskedEmail, LocalDateTime joinedAt) {}

    public record MemberBlog(long blogId, String name, String address, String visibility, String status,
                             boolean hidden, String role) {}

    public record OwnerWarning(long id, UserRef owner, String reason, UserRef admin, Long reportId,
                               LocalDateTime createdAt, boolean counted) {}

    public record OwnerWarningSummary(long blogId, String blogName, int activeCount, List<OwnerWarning> warnings) {}

    public record MemberDetail(long id, String nickname, String name, String maskedEmail, String maskedPhone,
                               LocalDateTime joinedAt, String status, List<MemberBlog> blogs,
                               List<OwnerWarningSummary> ownerWarnings) {}

    public record PrivacyView(String email, String phone, LocalDateTime viewedAt) {}

    private final JdbcTemplate jdbc;
    private final AdminGuard guard;
    private final AdminActionLog actionLog;
    private final AdminPaging paging;
    private final RateLimiter rateLimiter;
    private final AdminProperties props;
    private final Clock clock;

    public AdminMemberService(JdbcTemplate jdbc, AdminGuard guard, AdminActionLog actionLog, AdminPaging paging,
                              RateLimiter rateLimiter, AdminProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.actionLog = actionLog;
        this.paging = paging;
        this.rateLimiter = rateLimiter;
        this.props = props;
        this.clock = clock;
    }

    /** 닉네임 일부 일치(1~12자) 또는 이메일 전체 일치(대소문자 무시). 탈퇴 회원·관리자 계정은 빼고 최근 가입 순(D-103 #2·#3). */
    @Transactional(readOnly = true)
    public Page<MemberSummary> search(long adminId, String type, String q, Integer page, Integer size) {
        guard.require(adminId);
        int p = paging.page(page);
        int s = paging.size(size);
        String keyword = q == null ? "" : q.strip();
        String where;
        Object arg;
        if ("NICKNAME".equals(type)) {
            if (keyword.isEmpty() || keyword.codePointCount(0, keyword.length())
                    > props.member().nicknameSearchMaxLength()) {
                throw new InvalidInputException("q", "Size");
            }
            where = "u.nickname LIKE ?";
            arg = "%" + likeEscape(keyword) + "%";
        } else if ("EMAIL".equals(type)) {
            if (keyword.isEmpty()) {
                throw new InvalidInputException("q", "NotBlank");
            }
            where = "u.email = ?";
            arg = keyword.toLowerCase(Locale.ROOT);
        } else {
            throw new InvalidInputException("type", "Pattern");
        }
        String base = " FROM users u WHERE u.status = 'ACTIVE' AND u.role = 'USER' AND " + where;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + base, Long.class, arg);
        List<MemberSummary> items = jdbc.query("SELECT u.id, u.nickname, u.name, u.email, u.created_at" + base
                        + " ORDER BY u.created_at DESC, u.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new MemberSummary(rs.getLong("id"), rs.getString("nickname"), rs.getString("name"),
                        EmailMasker.mask(rs.getString("email")), rs.getTimestamp("created_at").toLocalDateTime()),
                arg, s, AdminPaging.offset(p, s));
        return Page.of(items, p, s, total == null ? 0 : total);
    }

    /** 상세: 가린 이메일·전화번호, 이름(가리지 않음, D-103 #4), 운영·참여 블로그, 블로그별 블로그장 경고 이력. 탈퇴 회원은 404. */
    @Transactional(readOnly = true)
    public MemberDetail detail(long adminId, long userId) {
        guard.require(adminId);
        Map<String, Object> u = activeUser(userId);
        List<MemberBlog> blogs = jdbc.query("""
                SELECT b.id, b.name, b.address, b.visibility, b.status, b.hidden, m.role FROM blog_members m
                JOIN blogs b ON b.id = m.blog_id WHERE m.user_id = ?
                ORDER BY FIELD(m.role, 'OWNER', 'MANAGER', 'MEMBER'), b.id""",
                (rs, i) -> new MemberBlog(rs.getLong("id"), rs.getString("name"), rs.getString("address"),
                        rs.getString("visibility"), rs.getString("status"), rs.getBoolean("hidden"),
                        rs.getString("role")), userId);
        return new MemberDetail(userId, (String) u.get("nickname"), (String) u.get("name"),
                EmailMasker.mask((String) u.get("email")), PhoneMasker.mask((String) u.get("phone")),
                (LocalDateTime) u.get("created_at"), "ACTIVE", blogs,
                warningsOf(userId));
    }

    /**
     * "전체 보기": 원래 이메일·전화번호. 사유는 받지 않는다(D-103 #5). 같은 트랜잭션에서 PRIVACY_VIEW를 기록하고,
     * 기록이 안 되면 값도 주지 않는다. 관리자 한 명당 요청 제한을 넘으면 429(research R-04).
     */
    @Transactional
    public PrivacyView privacyView(long adminId, long userId) {
        guard.require(adminId);
        String key = "admin-privacy-view:" + adminId;
        if (!rateLimiter.tryAcquire(key, props.privacyView().rateLimit(), props.privacyView().rateWindow())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED)
                    .with("retryAfterSeconds", rateLimiter.retryAfterSeconds(key, props.privacyView().rateWindow()));
        }
        Map<String, Object> u = activeUser(userId);
        actionLog.write(adminId, ActionType.PRIVACY_VIEW, TargetType.USER, userId, null, null);
        return new PrivacyView((String) u.get("email"), formatPhone((String) u.get("phone")),
                LocalDateTime.now(clock));
    }

    /** 블로그별 블로그장 경고 이력. 1년(admin.warning.window) 안이면 counted(D-95). */
    List<OwnerWarningSummary> warningsOf(long userId) {
        LocalDateTime since = LocalDateTime.now(clock).minus(props.warning().window());
        Map<Long, List<OwnerWarning>> byBlog = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        jdbc.query("""
                SELECT w.id, w.blog_id, b.name AS blog_name, w.reason, w.report_id, w.created_at, w.user_id,
                       ou.nickname AS owner_nickname, ou.status AS owner_status,
                       w.admin_id, au.nickname AS admin_nickname, au.status AS admin_status
                FROM owner_warnings w JOIN blogs b ON b.id = w.blog_id
                JOIN users ou ON ou.id = w.user_id JOIN users au ON au.id = w.admin_id
                WHERE w.user_id = ? ORDER BY w.blog_id, w.created_at DESC, w.id DESC""", rs -> {
            long blogId = rs.getLong("blog_id");
            names.put(blogId, rs.getString("blog_name"));
            LocalDateTime at = rs.getTimestamp("created_at").toLocalDateTime();
            byBlog.computeIfAbsent(blogId, k -> new ArrayList<>()).add(new OwnerWarning(rs.getLong("id"),
                    UserRef.of(rs.getLong("user_id"), rs.getString("owner_nickname"), rs.getString("owner_status")),
                    rs.getString("reason"),
                    UserRef.of(rs.getLong("admin_id"), rs.getString("admin_nickname"), rs.getString("admin_status")),
                    rs.getObject("report_id", Long.class), at, at.isAfter(since)));
        }, userId);
        List<OwnerWarningSummary> result = new ArrayList<>();
        byBlog.forEach((blogId, list) -> result.add(new OwnerWarningSummary(blogId, names.get(blogId),
                (int) list.stream().filter(OwnerWarning::counted).count(), list)));
        return result;
    }

    private Map<String, Object> activeUser(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT nickname, name, email, phone, created_at "
                + "FROM users WHERE id = ? AND status = 'ACTIVE'", userId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return rows.get(0);
    }

    /** 01012345678 → 010-1234-5678. 다른 모양이면 그대로. */
    static String formatPhone(String phone) {
        if (phone == null) {
            return null;
        }
        String d = phone.replaceAll("\\D", "");
        return d.length() == 11 ? d.substring(0, 3) + "-" + d.substring(3, 7) + "-" + d.substring(7) : phone;
    }

    /** LIKE 특수 문자(\, %, _)를 글자 그대로 찾게 한다(MySQL 기본 이스케이프 문자는 \). */
    public static String likeEscape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
