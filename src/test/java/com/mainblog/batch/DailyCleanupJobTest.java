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
    void 바꾸거나_지운_프로필_사진은_30일_뒤_행을_지우고_29일째는_그대로() {
        long id = createUser("u@x.com", "Abcd123!", "회원");
        String sql = "INSERT INTO images (uploader_id, owner_type, owner_id, stored_name, original_name, "
                + "content_type, size_bytes, deleted_at) VALUES (?, ?, ?, ?, 'a.jpg', 'image/jpeg', 10, ?)";
        jdbc.update(sql, id, "PROFILE", id, java.util.UUID.randomUUID() + ".jpg", BASE_TIME.minusDays(30));
        jdbc.update(sql, id, "PROFILE", id, java.util.UUID.randomUUID() + ".jpg", BASE_TIME.minusDays(29));
        jdbc.update(sql, id, "PROFILE", id, java.util.UUID.randomUUID() + ".jpg", null);
        jdbc.update(sql, id, "POST", null, java.util.UUID.randomUUID() + ".jpg", BASE_TIME.minusDays(40));

        DailyCleanupJob.Result result = job.runOnce();
        assertThat(result.profileImages()).isEqualTo(1);
        // 삭제 표시 30일 지난 글 이미지는 004 게시판 정리 단계가 지운다(004 T054)
        assertThat(result.board().purgedImages()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM images", Integer.class)).isEqualTo(2);
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

    @Test
    void 창이_끝난_요청_제한_장부를_지운다() {
        String sql = "INSERT INTO rate_limits (key_hash, window_start, request_count, expires_at) VALUES (?, ?, 1, ?)";
        jdbc.update(sql, "a".repeat(64), BASE_TIME.minusMinutes(2), BASE_TIME.minusSeconds(1));
        jdbc.update(sql, "b".repeat(64), BASE_TIME.minusSeconds(30), BASE_TIME.plusSeconds(30));

        assertThat(job.runOnce().rateLimits()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT key_hash FROM rate_limits", String.class)).containsExactly("b".repeat(64));
    }
}
