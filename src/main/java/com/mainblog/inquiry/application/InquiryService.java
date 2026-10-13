package com.mainblog.inquiry.application;

import com.mainblog.auth.application.CaptchaVerifier;
import com.mainblog.common.config.InquiryProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import com.mainblog.common.ratelimit.RateLimiter;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시스템 문의 보내기와 내 문의 (ADM-08, D-112).
 * - 회원은 로그인한 계정으로 보낸다. 이메일은 받지 않고 답변은 알림으로 간다.
 * - 비회원은 답변 받을 이메일을 적고 사람 확인(Turnstile, SEC-13, D-79)을 통과해야 한다. 답변은 메일로 간다.
 * - 회원·비회원 모두 같은 IP에서 inquiry.rate-window 동안 inquiry.rate-limit건까지.
 * - 문의가 들어오면 활동 중인 메인 관리자 모두에게 INQUIRY_RECEIVED 알림을 만든다. 알림에 이메일은 넣지 않는다(D-22).
 */
@Service
public class InquiryService {

    /** 형식만 본다. 실제로 받을 수 있는지는 답변 메일을 보낼 때 알 수 있다. */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final int EMAIL_MAX = 255;

    public record SubmitRequest(String title, String content, String email, String captchaToken) {}

    public record Submitted(long id) {}

    public record MyInquiry(long id, String title, String content, String status, String answer,
                            LocalDateTime createdAt, LocalDateTime answeredAt) {}

    public record Page<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}

    private static final int MINE_PAGE_SIZE = 10;

    private final JdbcTemplate jdbc;
    private final CaptchaVerifier captcha;
    private final RateLimiter rateLimiter;
    private final NotificationWriter notifications;
    private final InquiryProperties props;
    private final Clock clock;

    public InquiryService(JdbcTemplate jdbc, CaptchaVerifier captcha, RateLimiter rateLimiter,
                          NotificationWriter notifications, InquiryProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.captcha = captcha;
        this.rateLimiter = rateLimiter;
        this.notifications = notifications;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public Submitted submit(Long userId, SubmitRequest req, String clientIp) {
        String title = text("title", req.title(), props.titleMaxLength());
        String content = text("content", req.content(), props.contentMaxLength());
        Long sender = activeMember(userId);
        String email = null;
        if (sender == null) {
            email = email(req.email());
        }
        String key = "inquiry:" + clientIp;
        if (!rateLimiter.tryAcquire(key, props.rateLimit(), props.rateWindow())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED)
                    .with("retryAfterSeconds", rateLimiter.retryAfterSeconds(key, props.rateWindow()));
        }
        if (sender == null) {
            if (req.captchaToken() == null || req.captchaToken().isBlank()) {
                throw new BusinessException(ErrorCode.CAPTCHA_REQUIRED).with("captchaRequired", true);
            }
            if (!captcha.verify(req.captchaToken(), clientIp)) {
                throw new BusinessException(ErrorCode.CAPTCHA_FAILED).with("captchaRequired", true);
            }
        }
        long id = insert(sender, email, title, content);
        String note = "새 시스템 문의가 왔습니다: " + title;
        for (Long admin : jdbc.queryForList("SELECT id FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE'",
                Long.class)) {
            notifications.write(admin, NotificationType.INQUIRY_RECEIVED, "INQUIRY", id, null, note,
                    "INQUIRY_RECEIVED:" + id);
        }
        return new Submitted(id);
    }

    /** 로그인한 회원 자신의 문의. 최근 순. */
    @Transactional(readOnly = true)
    public Page<MyInquiry> mine(long userId, Integer page) {
        int p = page == null || page < 1 ? 1 : page;
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM system_inquiries WHERE user_id = ?", Long.class, userId);
        long t = total == null ? 0 : total;
        List<MyInquiry> items = jdbc.query("SELECT id, title, content, status, answer, created_at, answered_at "
                        + "FROM system_inquiries WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new MyInquiry(rs.getLong("id"), rs.getString("title"), rs.getString("content"),
                        rs.getString("status"), rs.getString("answer"),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("answered_at", LocalDateTime.class)),
                userId, MINE_PAGE_SIZE, (long) (p - 1) * MINE_PAGE_SIZE);
        return new Page<>(items, p, MINE_PAGE_SIZE, t, (int) ((t + MINE_PAGE_SIZE - 1) / MINE_PAGE_SIZE));
    }

    /** 토큰이 있어도 탈퇴한 회원이면 비회원처럼 다룬다. */
    private Long activeMember(Long userId) {
        if (userId == null) {
            return null;
        }
        String status = jdbc.query("SELECT status FROM users WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null,
                userId);
        return "ACTIVE".equals(status) ? userId : null;
    }

    private static String text(String field, String raw, int max) {
        String s = raw == null ? "" : raw.strip();
        if (s.isEmpty()) {
            throw new InvalidInputException(field, "NotBlank");
        }
        if (s.codePointCount(0, s.length()) > max) {
            throw new InvalidInputException(field, "Size");
        }
        return s;
    }

    private static String email(String raw) {
        String s = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            throw new InvalidInputException("email", "NotBlank");
        }
        if (s.length() > EMAIL_MAX || !EMAIL.matcher(s).matches()) {
            throw new InvalidInputException("email", "Email");
        }
        return s;
    }

    private long insert(Long userId, String email, String title, String content) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO system_inquiries (user_id, email, title, content, "
                    + "status, created_at, updated_at) VALUES (?, ?, ?, ?, 'PENDING', ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, userId);
            ps.setString(2, email);
            ps.setString(3, title);
            ps.setString(4, content);
            ps.setTimestamp(5, now);
            ps.setTimestamp(6, now);
            return ps;
        }, key);
        return key.getKey().longValue();
    }
}
