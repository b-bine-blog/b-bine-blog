package com.mainblog.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.mainblog.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 프로필·팔로워 목록·알림 응답 시간 (003 T048, SC-002). 팔로워 5,000명인 회원의 프로필·팔로워 목록,
 * 알림 3,000건인 회원의 알림 목록·안 읽은 개수가 2초 안에 답해야 한다.
 */
class SocialResponseTimeTest extends IntegrationTestBase {

    static final long LIMIT_MS = 2000;
    static final int FOLLOWERS = 5000;
    static final int NOTIFICATIONS = 3000;

    private long elapsedMs(String url, Cookie... cookies) throws Exception {
        long start = System.nanoTime();
        var req = get(URI.create(url));
        if (cookies.length > 0) {
            req.cookie(cookies);
        }
        int status = mvc.perform(req).andReturn().getResponse().getStatus();
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertThat(status).as(url).isEqualTo(200);
        return ms;
    }

    @Test
    void 팔로워_5000명_알림_3000건에서_2초_안에_답한다() throws Exception {
        long star = createUser("star@x.com", "Abcd123!", "인기인");
        List<Object[]> users = new ArrayList<>();
        for (int i = 0; i < FOLLOWERS; i++) {
            users.add(new Object[] {"f" + i + "@x.com", "팬" + i, String.format("010%08d", i), BASE_TIME});
        }
        jdbc.batchUpdate("INSERT INTO users (email, password_hash, name, nickname, phone, role, status, created_at) "
                + "VALUES (?, 'x', '팬', ?, ?, 'USER', 'ACTIVE', ?)", users);
        jdbc.update("INSERT INTO follows (follower_id, followee_id, created_at) "
                + "SELECT id, ?, DATE_SUB(?, INTERVAL id SECOND) FROM users WHERE id <> ?", star, BASE_TIME, star);

        List<Object[]> notes = new ArrayList<>();
        for (int i = 0; i < NOTIFICATIONS; i++) {
            String[] kinds = {"POST_COMMENT:COMMENT", "POST_LIKE:LIKE", "NEW_FOLLOWER:FOLLOW", "NOTICE:OPERATION"};
            String[] k = kinds[i % kinds.length].split(":");
            notes.add(new Object[] {star, k[0], k[1], "알림 " + i, "perf:" + i, i % 3 == 0,
                    BASE_TIME.minusMinutes(NOTIFICATIONS - i)});
        }
        jdbc.batchUpdate("INSERT INTO notifications (recipient_id, notification_type, tab, content, event_key, is_read, "
                + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", notes);

        Cookie[] login = loginAs("star@x.com", "Abcd123!", false).cookies();
        elapsedMs("/api/users/" + star + "/profile"); // 첫 요청의 클래스 로딩 시간을 뺀다

        assertThat(elapsedMs("/api/users/" + star + "/profile")).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/users/" + star + "/followers")).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/users/" + star + "/followers?page=200")).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/notifications", login)).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/notifications?tab=LIKE", login)).isLessThan(LIMIT_MS);
        assertThat(elapsedMs("/api/notifications/unread-count", login)).isLessThan(LIMIT_MS);
    }
}
