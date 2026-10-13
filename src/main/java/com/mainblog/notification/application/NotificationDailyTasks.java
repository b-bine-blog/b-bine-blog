package com.mainblog.notification.application;

import com.mainblog.common.config.NotificationProperties;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 보관 기간 정리 (003 T031, FR-056, FR-057, SCL-03, 4.5, D-102 #27, research R-08). DailyCleanupJob(ShedLock)이 04:00에 부른다.
 * 보관 7일을 고른 회원은 받은 지 7일, 그 밖(설정 행 없음 포함)은 30일이 지난 알림을 지운다.
 * 기준 시각보다 오래된 것을 모두 지우므로 7일로 줄인 다음 실행에 이미 지난 것도 지워지고, 실행하지 못한 날의 몫도 다음 실행에서 지워진다.
 */
@Component
public class NotificationDailyTasks {

    static final int BATCH = 1000;

    private final JdbcTemplate jdbc;
    private final NotificationProperties props;
    private final TransactionTemplate transaction;

    public NotificationDailyTasks(JdbcTemplate jdbc, NotificationProperties props,
                                  PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.props = props;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** 지운 알림 수. 1,000건씩 따로 트랜잭션으로 지운다. */
    public int run(LocalDateTime now) {
        int deleted = 0;
        LocalDateTime shortCutoff = now.minusDays(props.retention().shortDays());
        int n;
        do {
            n = inTx(() -> {
                List<Long> ids = jdbc.queryForList("SELECT n.id FROM notifications n JOIN notification_settings s "
                        + "ON s.user_id = n.recipient_id AND s.retention_days = ? WHERE n.created_at < ? LIMIT ?",
                        Long.class, props.retention().shortDays(), shortCutoff, BATCH);
                return deleteIds(ids);
            });
            deleted += n;
        } while (n == BATCH);
        LocalDateTime defaultCutoff = now.minusDays(props.retention().defaultDays());
        do {
            n = inTx(() -> jdbc.update("DELETE FROM notifications WHERE created_at < ? ORDER BY id LIMIT ?",
                    defaultCutoff, BATCH));
            deleted += n;
        } while (n == BATCH);
        return deleted;
    }

    private int deleteIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        String in = String.join(", ", ids.stream().map(String::valueOf).toList());
        return jdbc.update("DELETE FROM notifications WHERE id IN (" + in + ")");
    }

    private int inTx(java.util.function.Supplier<Integer> work) {
        Integer n = transaction.execute(status -> work.get());
        return n == null ? 0 : n;
    }
}
