package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** 블로그장 경고 (005 T026, US3-1·2·10·11, FR-020, FR-021, D-95, D-103 #12·#13). */
class OwnerWarningIntegrationTest extends AdminTestBase {

    private Res warn(Member admin, long blogId, long ownerId, String reason) throws Exception {
        return postAs(admin, "/api/admin/blogs/" + blogId + "/owner-warnings",
                json(java.util.Map.of("ownerId", ownerId, "reason", reason)));
    }

    private int revocations(long blogId) {
        return count("SELECT COUNT(*) FROM owner_revocations WHERE blog_id = ?", blogId);
    }

    @Test
    void 사유는_1자에서_500자까지() throws Exception {
        Member admin = admin("관리자1");
        BlogFixture b = blog("warn-reason", "PUBLIC");
        Res blank = warn(admin, b.blogId(), b.owner().id(), "   ");
        assertThat(blank.status()).isEqualTo(400);
        assertThat(warn(admin, b.blogId(), b.owner().id(), "가".repeat(501)).status()).isEqualTo(400);
        assertThat(postAs(admin, "/api/admin/blogs/" + b.blogId() + "/owner-warnings", "{\"reason\":\"사유\"}")
                .status()).isEqualTo(400);
        assertThat(warn(admin, b.blogId(), b.owner().id(), " " + "가".repeat(500) + " ").status()).isEqualTo(201);
        assertThat(count("SELECT COUNT(*) FROM owner_warnings")).isEqualTo(1);
    }

    @Test
    void 첫_경고는_사유와_3번_중_1번을_알리고_세_번째에_박탈된다() throws Exception {
        Member admin = admin("관리자2");
        BlogFixture b = blog("warn-three", "PUBLIC");
        Member reporter = member("신고자2");

        Res first = warn(admin, b.blogId(), b.owner().id(), "광고 글 반복");
        assertThat(first.status()).isEqualTo(201);
        assertThat(first.json().get("activeCount").asInt()).isEqualTo(1);
        assertThat(first.json().get("threshold").asInt()).isEqualTo(3);
        assertThat(first.json().get("revocation").isNull()).isTrue();
        assertThat(first.json().get("warning").get("reason").asText()).isEqualTo("광고 글 반복");
        String content = notificationContents(b.owner().id(), "OWNER_WARNED").get(0);
        assertThat(content).contains("광고 글 반복").contains("3번 중 1번").doesNotContain("신고자2");
        assertThat(jdbc.queryForObject("SELECT tab FROM notifications WHERE notification_type = 'OWNER_WARNED'",
                String.class)).isEqualTo("OPERATION");
        assertThat(actions("OWNER_WARNING")).hasSize(1);
        assertThat(actions("OWNER_WARNING").get(0).get("memo").toString()).contains("광고 글 반복");
        assertThat(jdbc.queryForObject("SELECT reason FROM owner_warnings", String.class)).isEqualTo("광고 글 반복");

        Res second = warn(admin, b.blogId(), b.owner().id(), "두 번째");
        assertThat(second.json().get("activeCount").asInt()).isEqualTo(2);
        assertThat(revocations(b.blogId())).isZero();
        assertThat(ownerOf(b.blogId())).isEqualTo(String.valueOf(b.owner().id()));

        Res third = warn(admin, b.blogId(), b.owner().id(), "세 번째");
        assertThat(third.json().get("activeCount").asInt()).isEqualTo(3);
        JsonNode rev = third.json().get("revocation");
        assertThat(rev.get("cause").asText()).isEqualTo("WARNING_3");
        assertThat(rev.get("revokedUser").get("id").asLong()).isEqualTo(b.owner().id());
        assertThat(revocations(b.blogId())).isEqualTo(1);
        assertThat(role(b.blogId(), b.owner().id())).isEqualTo("MEMBER");
        assertThat(notificationContents(b.owner().id(), "OWNER_WARNED").get(2)).contains("3번 중 3번");
        assertThat(notificationsOf(b.owner().id())).contains("OWNER_REVOKED");
        assertThat(actionTypes()).containsExactly("OWNER_WARNING", "OWNER_WARNING", "OWNER_WARNING", "OWNER_KICK",
                "CLOSE_SCHEDULE");

        Res after = warn(admin, b.blogId(), b.owner().id(), "네 번째");
        assertThat(after.status()).isEqualTo(409);
        assertThat(after.code()).isEqualTo("OWNER_CHANGED");
    }

    @Test
    void 일년_지난_경고는_세지_않고_블로그별로_센다() throws Exception {
        Member admin = admin("관리자3");
        BlogFixture a = blog("warn-a", "PUBLIC");
        long c = createBlog(a.owner(), "warn-c", "PUBLIC", "FREE");
        insertWarning(a.blogId(), a.owner().id(), admin.id(), clock.now().minusDays(400));
        insertWarning(a.blogId(), a.owner().id(), admin.id(), clock.now().minusDays(30));
        insertWarning(c, a.owner().id(), admin.id(), clock.now().minusDays(10));
        insertWarning(c, a.owner().id(), admin.id(), clock.now().minusDays(5));

        Res r = warn(admin, a.blogId(), a.owner().id(), "하나 더");
        assertThat(r.json().get("activeCount").asInt()).isEqualTo(2);
        assertThat(revocations(a.blogId())).isZero();
        assertThat(revocations(c)).isZero();

        JsonNode list = getAs(admin, "/api/admin/blogs/" + a.blogId() + "/owner-warnings").json().get("warnings");
        assertThat(list).hasSize(3);
        assertThat(list.get(0).get("counted").asBoolean()).isTrue();
        assertThat(list.get(2).get("counted").asBoolean()).isFalse();
        assertThat(getAs(admin, "/api/admin/blogs/999999/owner-warnings").status()).isEqualTo(404);
    }

    @Test
    void 새_블로그장은_0부터_세고_전_블로그장이_돌아오면_자기_경고를_다시_센다() throws Exception {
        Member admin = admin("관리자4");
        BlogFixture b = blog("warn-new", "PUBLIC");
        Member s = member("새장4");
        addMember(b.blogId(), s.id(), "MANAGER");
        insertWarning(b.blogId(), b.owner().id(), admin.id(), clock.now().minusDays(3));
        insertWarning(b.blogId(), b.owner().id(), admin.id(), clock.now().minusDays(2));

        Res kick = postAs(admin, "/api/admin/blogs/" + b.blogId() + "/owner-revocation",
                "{\"ownerId\":" + b.owner().id() + "}");
        assertThat(kick.status()).isEqualTo(201);
        Res first = warn(admin, b.blogId(), s.id(), "새 블로그장 첫 경고");
        assertThat(first.json().get("activeCount").asInt()).isEqualTo(1);

        // 전 블로그장이 다시 블로그장이 되면 자기 경고 2번을 다시 센다
        jdbc.update("UPDATE blog_members SET role = 'MEMBER' WHERE blog_id = ? AND user_id = ?", b.blogId(), s.id());
        jdbc.update("UPDATE blog_members SET role = 'OWNER' WHERE blog_id = ? AND user_id = ?", b.blogId(),
                b.owner().id());
        Res back = warn(admin, b.blogId(), b.owner().id(), "돌아온 뒤 경고");
        assertThat(back.json().get("activeCount").asInt()).isEqualTo(3);
        assertThat(back.json().get("revocation").get("cause").asText()).isEqualTo("WARNING_3");
    }

    @Test
    void 지금_블로그장이_아니면_409이고_폐쇄된_블로그도_409() throws Exception {
        Member admin = admin("관리자5");
        BlogFixture b = blog("warn-changed", "PUBLIC");
        Member m = memberOf(b.blogId(), "멤버5");
        Res wrong = warn(admin, b.blogId(), m.id(), "멤버에게");
        assertThat(wrong.status()).isEqualTo(409);
        assertThat(wrong.code()).isEqualTo("OWNER_CHANGED");
        jdbc.update("UPDATE blogs SET status = 'CLOSED', closed_at = ? WHERE id = ?", clock.now(), b.blogId());
        assertThat(warn(admin, b.blogId(), b.owner().id(), "닫힘").code()).isEqualTo("BLOG_ALREADY_CLOSED");
        assertThat(warn(admin, 999999, b.owner().id(), "없음").status()).isEqualTo(404);
        assertThat(count("SELECT COUNT(*) FROM owner_warnings")).isZero();
        assertThat(notificationsOf(m.id())).isEmpty();
    }
}
