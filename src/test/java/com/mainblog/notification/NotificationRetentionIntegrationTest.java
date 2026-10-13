package com.mainblog.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.social.SocialTestBase;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 알림 보관 기간 정리 (003 T029, US5-5~7, SC-006, FR-056, FR-057, D-102 #27). 04:00 정리(DailyCleanupJob)로 지운다. */
class NotificationRetentionIntegrationTest extends SocialTestBase {

    private static final LocalDateTime RECEIVED = LocalDateTime.of(2026, 10, 8, 9, 0);

    private void runAt(LocalDateTime at) {
        clock.setTo(at);
        runDaily();
    }

    @Test
    void 기본_30일_지나면_04시_정리에서_사라진다() throws Exception {
        Member m = member("기본");
        insertNotification(m.id(), "POST_LIKE", "LIKE", RECEIVED);
        runAt(RECEIVED.plusDays(29).withHour(4));
        assertThat(notificationCount(m.id())).isEqualTo(1);
        runAt(RECEIVED.plusDays(30).withHour(4)); // 아직 30일이 다 지나지 않았다
        assertThat(notificationCount(m.id())).isEqualTo(1);
        runAt(RECEIVED.plusDays(31).withHour(4));
        assertThat(notificationCount(m.id())).isZero();
    }

    @Test
    void 칠일로_줄이면_다음_04시에_이미_7일_지난_것까지_지워지고_30일로_되돌려도_돌아오지_않는다() throws Exception {
        Member m = member("짧게");
        Member other = member("남");
        insertNotification(m.id(), "POST_LIKE", "LIKE", RECEIVED.minusDays(10));
        insertNotification(m.id(), "POST_LIKE", "LIKE", RECEIVED.minusDays(3));
        insertNotification(other.id(), "POST_LIKE", "LIKE", RECEIVED.minusDays(10));
        assertThat(putAs(m, "/api/me/notification-settings", "{\"retentionDays\":7}").status()).isEqualTo(200);
        runAt(RECEIVED.plusDays(1).withHour(4));
        assertThat(notificationCount(m.id())).isEqualTo(1);
        assertThat(notificationCount(other.id())).isEqualTo(1); // 다른 회원은 30일 기준 그대로

        setRetention(m.id(), 30);
        runAt(RECEIVED.plusDays(2).withHour(4));
        assertThat(notificationCount(m.id())).isEqualTo(1);
    }

    @Test
    void 하루_건너뛰어도_다음_실행에서_밀린_것까지_지운다() throws Exception {
        Member m = member("밀림");
        setRetention(m.id(), 7);
        insertNotification(m.id(), "POST_LIKE", "LIKE", RECEIVED);
        insertNotification(m.id(), "POST_LIKE", "LIKE", RECEIVED.plusDays(1));
        insertNotification(m.id(), "POST_LIKE", "LIKE", RECEIVED.plusDays(5));
        // 8일째·9일째 04:00에 서버가 꺼져 있었다
        runAt(RECEIVED.plusDays(10).withHour(4));
        assertThat(notificationCount(m.id())).isEqualTo(1);
    }

    @Test
    void 많으면_1000건씩_나눠_모두_지운다() throws Exception {
        Member m = member("대량");
        jdbc.update("INSERT INTO notifications (recipient_id, notification_type, tab, content, event_key, created_at) "
                + "SELECT ?, 'POST_LIKE', 'LIKE', '좋아요', CONCAT('BULK:', n), ? FROM (SELECT a.N + b.N * 10 + c.N * 100 "
                + "+ d.N * 1000 AS n FROM (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 "
                + "UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a, "
                + "(SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 "
                + "UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b, (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 "
                + "UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c, "
                + "(SELECT 0 N UNION SELECT 1 UNION SELECT 2) d) t WHERE n < 2500", m.id(), RECEIVED.minusDays(40));
        assertThat(notificationCount(m.id())).isEqualTo(2500);
        runAt(RECEIVED.withHour(4));
        assertThat(notificationCount(m.id())).isZero();
    }
}
