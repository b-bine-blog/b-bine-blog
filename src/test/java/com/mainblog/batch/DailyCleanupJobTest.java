package com.mainblog.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 04:00 정리 작업 (T046, 4.5, D-83, US3 인수 시나리오 6). */
class DailyCleanupJobTest extends IntegrationTestBase {

    @Autowired
    DailyCleanupJob job;

    private long withdrawnUser(String email, String nickname, int daysAgo) {
        long id = createUser(email, "Abcd123!", nickname);
        jdbc.update("UPDATE users SET status='WITHDRAWN', withdrawn_email=email, email=NULL, bio='소개', "
                + "withdrawn_at=? WHERE id=?", BASE_TIME.minusDays(daysAgo), id);
        jdbc.update("INSERT INTO images (uploader_id, owner_type, owner_id, stored_name, original_name, content_type, "
                + "size_bytes) VALUES (?, 'PROFILE', ?, ?, 'me.jpg', 'image/jpeg', 10)", id, id,
                java.util.UUID.randomUUID() + ".jpg");
        return id;
    }

    @Test
    void 탈퇴_30일이_지나면_개인정보와_프로필_사진을_지우고_29일째는_그대로() throws Exception {
        long old = withdrawnUser("old@x.com", "오래됨", 30);
        long recent = withdrawnUser("new@x.com", "최근", 29);

        assertThat(job.runOnce().purgedMembers()).isEqualTo(1);

        Map<String, Object> purged = jdbc.queryForMap(
                "SELECT withdrawn_email, name, nickname, phone, bio, status FROM users WHERE id=?", old);
        assertThat(purged).containsEntry("withdrawn_email", null).containsEntry("name", null)
                .containsEntry("nickname", null).containsEntry("phone", null).containsEntry("bio", null)
                .containsEntry("status", "WITHDRAWN");
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM images WHERE owner_id=?", Boolean.class, old))
                .isTrue();

        assertThat(jdbc.queryForObject("SELECT nickname FROM users WHERE id=?", String.class, recent)).isEqualTo("최근");
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM images WHERE owner_id=?", Boolean.class, recent))
                .isTrue();
        // 두 번 돌려도 이미 지운 회원은 다시 세지 않는다
        assertThat(job.runOnce().purgedMembers()).isZero();
    }

    @Test
    void 만료되거나_폐기된_RT를_지운다() {
        long id = createUser("u@x.com", "Abcd123!", "회원");
        String sql = "INSERT INTO refresh_tokens (user_id, token_hash, remember_me, last_active_at, expires_at, revoked) "
                + "VALUES (?, ?, TRUE, ?, ?, ?)";
        jdbc.update(sql, id, "expired", BASE_TIME, BASE_TIME.minusSeconds(1), false);
        jdbc.update(sql, id, "revoked", BASE_TIME, BASE_TIME.plusDays(3), true);
        jdbc.update(sql, id, "alive", BASE_TIME, BASE_TIME.plusDays(3), false);

        assertThat(job.runOnce().refreshTokens()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT token_hash FROM refresh_tokens", String.class)).containsExactly("alive");
    }

    @Test
    void 마지막_실패가_1일_지난_로그인_실패_기록을_지운다() {
        String sql = "INSERT INTO login_failures (email, fail_count, last_failed_at) VALUES (?, 2, ?)";
        jdbc.update(sql, "old@x.com", BASE_TIME.minusDays(1).minusSeconds(1));
        jdbc.update(sql, "new@x.com", BASE_TIME.minusHours(23));

        assertThat(job.runOnce().loginFailures()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT email FROM login_failures", String.class)).containsExactly("new@x.com");
    }
}
