package com.mainblog.common.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 알림 쓰기 (T013, research R-14). */
class NotificationWriterTest extends IntegrationTestBase {

    @Autowired
    NotificationWriter writer;

    @Test
    void 같은_이벤트는_한_번만_만든다() {
        long u = createUser("n1@test.com", "Passw0rd!", "알림1");
        assertThat(writer.write(u, NotificationType.BLOG_CLOSING, "BLOG", 1L, null, "폐쇄 예정", "K1")).isTrue();
        assertThat(writer.write(u, NotificationType.BLOG_CLOSING, "BLOG", 1L, null, "폐쇄 예정", "K1")).isFalse();
        assertThat(notificationsOf(u)).containsExactly("BLOG_CLOSING");
    }

    @Test
    void 끈_알림은_만들지_않고_끌_수_없는_알림은_만든다() {
        long u = createUser("n2@test.com", "Passw0rd!", "알림2");
        jdbc.update("INSERT INTO notification_settings (user_id, join_result) VALUES (?, FALSE)", u);
        assertThat(writer.write(u, NotificationType.JOIN_APPROVED, "BLOG", 1L, null, "승인", "K2")).isFalse();
        assertThat(writer.write(u, NotificationType.MEMBER_KICKED, "BLOG", 1L, null, "강퇴", "K3")).isTrue();
        assertThat(notificationsOf(u)).containsExactly("MEMBER_KICKED");
    }
}
