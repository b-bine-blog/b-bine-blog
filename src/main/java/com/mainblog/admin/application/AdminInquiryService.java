package com.mainblog.admin.application;

import com.mainblog.admin.application.AdminViews.Page;
import com.mainblog.admin.application.AdminViews.UserRef;
import com.mainblog.common.config.InquiryProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.masking.EmailMasker;
import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import com.mainblog.inquiry.application.InquiryMailSender;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 관리자 시스템 문의함과 답변 (ADM-08, D-112).
 * 대기 중은 오래된 순, 답변 완료는 최근 답변 순(신고함과 같은 순서, research R-10).
 * 비회원 이메일은 가려서 준다(D-22). 답변은 한 번만 달고 바꿀 수 없다. 회원에게는 알림, 비회원에게는 커밋 뒤 메일을 보낸다.
 */
@Service
public class AdminInquiryService {

    public record InquirySummary(long id, String title, String status, UserRef sender, String maskedEmail,
                                 LocalDateTime createdAt, LocalDateTime answeredAt) {}

    public record InquiryDetail(long id, String title, String content, String status, UserRef sender,
                                String maskedEmail, String answer, UserRef answeredBy, LocalDateTime createdAt,
                                LocalDateTime answeredAt) {}

    private static final String SELECT = """
            SELECT i.id, i.title, i.content, i.status, i.email, i.answer, i.created_at, i.answered_at,
                   i.user_id, su.nickname AS sender_nickname, su.status AS sender_status,
                   i.answered_by, au.nickname AS answered_nickname, au.status AS answered_status
            FROM system_inquiries i LEFT JOIN users su ON su.id = i.user_id LEFT JOIN users au ON au.id = i.answered_by
            """;

    private record Row(long id, Long userId, String email, String title, String content, String status) {}

    private final JdbcTemplate jdbc;
    private final AdminGuard guard;
    private final AdminActionLog actionLog;
    private final AdminPaging paging;
    private final NotificationWriter notifications;
    private final InquiryMailSender mailSender;
    private final InquiryProperties props;
    private final Clock clock;

    public AdminInquiryService(JdbcTemplate jdbc, AdminGuard guard, AdminActionLog actionLog, AdminPaging paging,
                               NotificationWriter notifications, InquiryMailSender mailSender,
                               InquiryProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.actionLog = actionLog;
        this.paging = paging;
        this.notifications = notifications;
        this.mailSender = mailSender;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<InquirySummary> list(long adminId, String status, Integer page, Integer size) {
        guard.require(adminId);
        String s = status == null || status.isBlank() ? "PENDING" : status;
        if (!s.equals("PENDING") && !s.equals("ANSWERED")) {
            throw new InvalidInputException("status", "Pattern");
        }
        int p = paging.page(page);
        int n = paging.size(size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM system_inquiries WHERE status = ?", Long.class, s);
        String order = s.equals("PENDING") ? "i.created_at ASC, i.id ASC" : "i.answered_at DESC, i.id DESC";
        List<InquirySummary> items = jdbc.query(SELECT + " WHERE i.status = ? ORDER BY " + order + " LIMIT ? OFFSET ?",
                (rs, i) -> {
                    InquiryDetail d = detail(rs);
                    return new InquirySummary(d.id(), d.title(), d.status(), d.sender(), d.maskedEmail(),
                            d.createdAt(), d.answeredAt());
                }, s, n, AdminPaging.offset(p, n));
        return Page.of(items, p, n, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public InquiryDetail detail(long adminId, long inquiryId) {
        guard.require(adminId);
        return load(inquiryId);
    }

    @Transactional
    public InquiryDetail answer(long adminId, long inquiryId, String rawAnswer) {
        guard.require(adminId);
        String answer = rawAnswer == null ? "" : rawAnswer.strip();
        if (answer.isEmpty()) {
            throw new InvalidInputException("answer", "NotBlank");
        }
        if (answer.codePointCount(0, answer.length()) > props.answerMaxLength()) {
            throw new InvalidInputException("answer", "Size");
        }
        List<Row> rows = jdbc.query("SELECT id, user_id, email, title, content, status FROM system_inquiries "
                        + "WHERE id = ? FOR UPDATE",
                (rs, i) -> new Row(rs.getLong("id"), rs.getObject("user_id", Long.class), rs.getString("email"),
                        rs.getString("title"), rs.getString("content"), rs.getString("status")), inquiryId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Row row = rows.get(0);
        if ("ANSWERED".equals(row.status())) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }
        jdbc.update("UPDATE system_inquiries SET status = 'ANSWERED', answer = ?, answered_by = ?, answered_at = ? "
                + "WHERE id = ?", answer, adminId, LocalDateTime.now(clock), inquiryId);
        actionLog.write(adminId, ActionType.INQUIRY_ANSWER, TargetType.INQUIRY, inquiryId, null, row.title());
        if (row.userId() != null) {
            notifications.write(row.userId(), NotificationType.INQUIRY_ANSWERED, "INQUIRY", inquiryId, null,
                    "시스템 문의 '" + row.title() + "'에 답변이 달렸습니다.", "INQUIRY_ANSWERED:" + inquiryId);
        } else if (row.email() != null) {
            afterCommit(() -> mailSender.sendAnswer(row.email(), inquiryId, row.title(), row.content(), answer));
        }
        return load(inquiryId);
    }

    private static void afterCommit(Runnable work) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                work.run();
            }
        });
    }

    private InquiryDetail load(long inquiryId) {
        List<InquiryDetail> rows = jdbc.query(SELECT + " WHERE i.id = ?", (rs, i) -> detail(rs), inquiryId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return rows.get(0);
    }

    private static InquiryDetail detail(ResultSet rs) throws SQLException {
        String email = rs.getString("email");
        return new InquiryDetail(rs.getLong("id"), rs.getString("title"), rs.getString("content"),
                rs.getString("status"),
                UserRef.of(rs.getObject("user_id", Long.class), rs.getString("sender_nickname"),
                        rs.getString("sender_status")),
                email == null ? null : EmailMasker.mask(email), rs.getString("answer"),
                UserRef.of(rs.getObject("answered_by", Long.class), rs.getString("answered_nickname"),
                        rs.getString("answered_status")),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("answered_at", LocalDateTime.class));
    }
}
