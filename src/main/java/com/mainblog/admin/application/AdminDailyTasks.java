package com.mainblog.admin.application;

import com.mainblog.common.config.AdminProperties;
import com.mainblog.common.config.InquiryProperties;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.IntSupplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 운영 기록 1년 정리 (005 T048, FR-038, 4.5, D-95, D-103 #19, SCL-03, research R-13). DailyCleanupJob(ShedLock)이 마지막에 부른다.
 * 기준은 기록이 생긴 날 + admin.records.retention(1년). 단계마다 트랜잭션을 따로 쓴다.
 * 아직 효력이 있는 정지(풀리지 않은 영구 정지 등)와 블랙리스트가 가리키는 강제 퇴장 기록은 남긴다.
 * 신고를 지우기 전에 남는 행이 가리키는 report_id를 NULL로 바꿔 외래 키를 푼다.
 */
@Component
public class AdminDailyTasks {

    private final JdbcTemplate jdbc;
    private final AdminProperties props;
    private final InquiryProperties inquiryProps;
    private final TransactionTemplate transaction;

    public AdminDailyTasks(JdbcTemplate jdbc, AdminProperties props, InquiryProperties inquiryProps,
                           PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.props = props;
        this.inquiryProps = inquiryProps;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public Result run(LocalDateTime now) {
        LocalDateTime cutoff = now.minus(props.records().retention());
        int actions = inTx(() -> jdbc.update("DELETE FROM admin_actions WHERE created_at < ?", cutoff));
        int warnings = inTx(() -> jdbc.update("DELETE FROM owner_warnings WHERE created_at < ?", cutoff));
        int revocations = inTx(() -> jdbc.update("DELETE FROM owner_revocations WHERE created_at < ?", cutoff));
        int sanctions = inTx(() -> purgeSanctions(cutoff, now));
        int reports = inTx(() -> purgeReports(cutoff));
        // 시스템 문의는 따로 정한 보관 기간(ADM-08, D-112). 비회원 이메일이 들어 있어 기간이 지나면 지운다
        LocalDateTime inquiryCutoff = now.minus(inquiryProps.retention());
        int inquiries = inTx(() -> jdbc.update("DELETE FROM system_inquiries WHERE created_at < ?", inquiryCutoff));
        return new Result(actions, warnings, revocations, sanctions, reports, inquiries);
    }

    /**
     * 1년 지난 멤버 제재 기록. 효력 있는 정지(뒤에 해제가 없는 정지)와 블랙리스트가 가리키는 강제 퇴장은 남긴다.
     * 기준 시각은 제재한 시각(starts_at)이다. 002가 created_at을 DB 기본값으로 채워서 서버 시계(Clock)와 맞추려고 쓴다.
     */
    private int purgeSanctions(LocalDateTime cutoff, LocalDateTime now) {
        List<Long> ids = jdbc.queryForList("""
                SELECT s.id FROM blog_sanctions s
                WHERE s.starts_at < ?
                  AND NOT EXISTS (SELECT 1 FROM blog_blacklists bl WHERE bl.sanction_id = s.id)
                  AND NOT (s.sanction_type = 'SUSPEND' AND (s.ends_at IS NULL OR s.ends_at > ?)
                           AND NOT EXISTS (SELECT 1 FROM blog_sanctions u WHERE u.blog_id = s.blog_id
                                           AND u.user_id = s.user_id AND u.sanction_type = 'UNSUSPEND' AND u.id > s.id))
                """, Long.class, cutoff, now);
        for (Long id : ids) {
            jdbc.update("DELETE FROM blog_sanctions WHERE id = ?", id);
        }
        return ids.size();
    }

    /** 1년 지난 신고. 남는 행(활동 기록·경고·제재)이 가리키면 report_id를 먼저 비운다. */
    private int purgeReports(LocalDateTime cutoff) {
        String old = "(SELECT r.id FROM reports r WHERE r.created_at < ?)";
        jdbc.update("UPDATE admin_actions SET report_id = NULL WHERE report_id IN " + old, cutoff);
        jdbc.update("UPDATE owner_warnings SET report_id = NULL WHERE report_id IN " + old, cutoff);
        jdbc.update("UPDATE blog_sanctions SET report_id = NULL WHERE report_id IN " + old, cutoff);
        return jdbc.update("DELETE FROM reports WHERE created_at < ?", cutoff);
    }

    private int inTx(IntSupplier work) {
        Integer n = transaction.execute(status -> work.getAsInt());
        return n == null ? 0 : n;
    }

    public record Result(int actions, int warnings, int revocations, int sanctions, int reports, int inquiries) {

        public static final Result EMPTY = new Result(0, 0, 0, 0, 0, 0);
    }
}
