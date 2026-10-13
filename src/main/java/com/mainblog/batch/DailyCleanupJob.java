package com.mainblog.batch;

import com.mainblog.admin.application.AdminDailyTasks;
import com.mainblog.auth.domain.LoginFailureRepository;
import com.mainblog.auth.domain.RefreshTokenRepository;
import com.mainblog.common.config.AuthProperties;
import com.mainblog.common.config.MemberProperties;
import com.mainblog.common.storage.FileStorage;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.mainblog.blog.application.BlogDailyTasks;
import com.mainblog.board.application.BoardDailyTasks;
import com.mainblog.notification.application.NotificationDailyTasks;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 매일 04:00(한국 시간) 정리 작업 (T050, 4.5, SCL-03, D-83, R-13).
 * 서버가 여러 대여도 ShedLock으로 한 번만 돈다. 블로그 폐쇄·정리 단계는 BlogDailyTasks(002)가 하고,
 * 1년 지난 운영 기록 정리는 AdminDailyTasks(005 T048)가 마지막에 한다.
 * 실행하지 못한 날이 있어도 기준 시각 이전 것을 모두 지우므로 다음 실행에서 밀린 것까지 처리된다.
 */
@Component
public class DailyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(DailyCleanupJob.class);

    private final JdbcTemplate jdbc;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginFailureRepository loginFailureRepository;
    private final FileStorage fileStorage;
    private final AuthProperties authProperties;
    private final MemberProperties memberProperties;
    private final BlogDailyTasks blogDailyTasks;
    private final BoardDailyTasks boardDailyTasks;
    private final NotificationDailyTasks notificationDailyTasks;
    private final AdminDailyTasks adminDailyTasks;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public DailyCleanupJob(JdbcTemplate jdbc, RefreshTokenRepository refreshTokenRepository,
                           LoginFailureRepository loginFailureRepository, FileStorage fileStorage,
                           AuthProperties authProperties, MemberProperties memberProperties,
                           BlogDailyTasks blogDailyTasks, BoardDailyTasks boardDailyTasks,
                           NotificationDailyTasks notificationDailyTasks, AdminDailyTasks adminDailyTasks,
                           PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginFailureRepository = loginFailureRepository;
        this.fileStorage = fileStorage;
        this.authProperties = authProperties;
        this.memberProperties = memberProperties;
        this.blogDailyTasks = blogDailyTasks;
        this.boardDailyTasks = boardDailyTasks;
        this.notificationDailyTasks = notificationDailyTasks;
        this.adminDailyTasks = adminDailyTasks;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "dailyCleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void scheduled() {
        Result result = runOnce();
        log.info("04:00 정리: 탈퇴 개인정보 {}명, RT {}건, 로그인 실패 기록 {}건, 인증번호 {}건, 지운 프로필 사진 {}건, "
                        + "위임 만료 {}건, 폐쇄 알림 {}건, 폐쇄 {}개, 30일 정리 {}개, 지운 대표 이미지 {}건, "
                        + "글 {}개, 댓글 {}개, 연결 안 된 글 이미지 {}건, 지운 글 이미지 {}건, 조회 기록 {}건, 보관 기간 지난 알림 {}건, "
                        + "1년 지난 관리자 활동 {}건·블로그장 경고 {}건·권한 박탈 {}건·멤버 제재 {}건·신고 {}건, 보관 기간 지난 시스템 문의 {}건",
                result.purgedMembers(), result.refreshTokens(), result.loginFailures(), result.codes(),
                result.profileImages(), result.blog().expiredDelegations(), result.blog().closingNotices(), result.blog().closedBlogs(),
                result.blog().purgedBlogs(), result.blog().coverImages(), result.board().purgedPosts(),
                result.board().purgedComments(), result.board().orphanImages(), result.board().purgedImages(),
                result.board().purgedViews(), result.notifications(), result.admin().actions(),
                result.admin().warnings(), result.admin().revocations(), result.admin().sanctions(),
                result.admin().reports(), result.admin().inquiries());
    }

    /** 회원 정리는 한 트랜잭션, 블로그·게시판 단계는 BlogDailyTasks·BoardDailyTasks가 단계마다 따로 트랜잭션을 쓴다. */
    public Result runOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        Result members = transaction.execute(status -> {
            int purged = purgeWithdrawnMembers(now);
            int tokens = refreshTokenRepository.deleteExpiredOrRevoked(now);
            int failures = loginFailureRepository.deleteByLastFailedAtBefore(
                    now.minus(authProperties.login().failureRetention()));
            int codes = deleteExpiredCodes(now);
            int images = purgeDeletedProfileImages(now);
            return new Result(purged, tokens, failures, codes, images, BlogDailyTasks.Result.EMPTY,
                    BoardDailyTasks.Result.EMPTY, 0, AdminDailyTasks.Result.EMPTY);
        });
        BlogDailyTasks.Result blog = blogDailyTasks.run(now);
        BoardDailyTasks.Result board = boardDailyTasks.run(now);
        int notifications = notificationDailyTasks.run(now); // 003 T031: 보관 기간(30일, 설정 7일) 지난 알림
        AdminDailyTasks.Result admin = adminDailyTasks.run(now); // 005 T048: 1년 지난 운영 기록(D-103 #19)
        return new Result(members.purgedMembers(), members.refreshTokens(), members.loginFailures(),
                members.codes(), members.profileImages(), blog, board, notifications, admin);
    }

    /** 탈퇴 30일 뒤 개인정보 삭제(4.5, USR-05 인수 시나리오 6). 행은 남겨 예전 글이 "탈퇴한 회원"을 가리키게 한다. */
    private int purgeWithdrawnMembers(LocalDateTime now) {
        LocalDateTime cutoff = now.minus(memberProperties.withdrawal().retention());
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM users
                WHERE status = 'WITHDRAWN' AND withdrawn_at <= ?
                  AND (withdrawn_email IS NOT NULL OR name IS NOT NULL OR nickname IS NOT NULL
                       OR phone IS NOT NULL OR bio IS NOT NULL)""", Long.class, cutoff);
        for (Long id : ids) {
            jdbc.update("UPDATE users SET withdrawn_email = NULL, name = NULL, nickname = NULL, phone = NULL, "
                    + "bio = NULL WHERE id = ?", id);
            List<String> files = jdbc.queryForList("SELECT stored_name FROM images WHERE owner_type = 'PROFILE' "
                    + "AND owner_id = ? AND deleted_at IS NULL", String.class, id);
            jdbc.update("UPDATE images SET deleted_at = ? WHERE owner_type = 'PROFILE' AND owner_id = ? "
                    + "AND deleted_at IS NULL", now, id);
            for (String file : files) {
                deleteQuietly(file);
            }
        }
        return ids.size();
    }

    /** 바꾸거나 지운 프로필 사진은 30일 뒤 파일과 행을 완전히 지운다(FR-043, images 테이블 설명). */
    private int purgeDeletedProfileImages(LocalDateTime now) {
        LocalDateTime cutoff = now.minus(memberProperties.profileImage().purgeAfter());
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, stored_name FROM images "
                + "WHERE owner_type = 'PROFILE' AND deleted_at <= ?", cutoff);
        for (Map<String, Object> row : rows) {
            deleteQuietly((String) row.get("stored_name"));
            jdbc.update("DELETE FROM images WHERE id = ?", row.get("id"));
        }
        return rows.size();
    }

    /** 만료된 인증번호·임시 토큰(FR-004, FR-031, FR-038). 쓰이면 서비스가 바로 지우고, 남은 것만 여기서 지운다. */
    private int deleteExpiredCodes(LocalDateTime now) {
        LocalDateTime verifiedCutoff = now.minus(authProperties.signup().verifiedTtl());
        int count = jdbc.update("DELETE FROM email_verifications WHERE expires_at <= ? "
                + "AND (verified_at IS NULL OR verified_at <= ?)", now, verifiedCutoff);
        count += jdbc.update("DELETE FROM password_reset_codes WHERE expires_at <= ?", now);
        count += jdbc.update("DELETE FROM password_reset_tokens WHERE expires_at <= ?", now);
        return count;
    }

    private void deleteQuietly(String storedName) {
        try {
            fileStorage.delete(storedName);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("프로필 사진 파일 삭제 실패: {}", e.getClass().getSimpleName());
        }
    }

    public record Result(int purgedMembers, int refreshTokens, int loginFailures, int codes, int profileImages,
                         BlogDailyTasks.Result blog, BoardDailyTasks.Result board, int notifications,
                         AdminDailyTasks.Result admin) {}
}
