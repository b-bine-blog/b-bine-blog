package com.mainblog.common.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 알림 쓰기 (T013, research R-14). 003 T005: 탈퇴한 받는 사람, 차단한 회원의 행동(research R-07, D-102 #11). */
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

    @Test
    void 탈퇴한_회원에게는_어떤_알림도_만들지_않는다() {
        long u = createUser("n3@test.com", "Passw0rd!", "알림3");
        jdbc.update("UPDATE users SET status = 'WITHDRAWN', email = NULL WHERE id = ?", u);
        assertThat(writer.write(u, NotificationType.BLOG_CLOSING, "BLOG", 1L, null, "폐쇄 예정", "K4")).isFalse();
        assertThat(notificationsOf(u)).isEmpty();
    }

    @Test
    void 차단한_회원의_행동은_끌_수_있는_알림만_막고_운영_알림은_그대로() {
        long u = createUser("n4@test.com", "Passw0rd!", "알림4");
        long actor = createUser("n5@test.com", "Passw0rd!", "알림5");
        long other = createUser("n6@test.com", "Passw0rd!", "알림6");
        jdbc.update("INSERT INTO user_blocks (blocker_id, blocked_id) VALUES (?, ?)", u, actor);
        assertThat(writer.write(u, NotificationType.POST_COMMENT, "COMMENT", 1L, actor, "댓글", "K5")).isFalse();
        assertThat(writer.write(u, NotificationType.NEW_FOLLOWER, "USER", actor, actor, "팔로우", "K6")).isFalse();
        assertThat(writer.write(u, NotificationType.BLOG_SUBSCRIBED, "BLOG", 1L, actor, "구독", "K7")).isFalse();
        assertThat(writer.write(u, NotificationType.DELEGATION_REQUESTED, "BLOG", 1L, actor, "위임", "K8")).isTrue();
        assertThat(writer.write(u, NotificationType.POST_LIKE, "POST", 1L, other, "좋아요", "K9")).isTrue();
        // 차단당한 쪽이 받는 알림은 막지 않는다(숨김은 차단한 쪽만)
        assertThat(writer.write(actor, NotificationType.POST_LIKE, "POST", 1L, u, "좋아요", "K10")).isTrue();
        assertThat(notificationsOf(u)).containsExactly("DELEGATION_REQUESTED", "POST_LIKE");
    }
}
