package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** US2 참여 신청 (T030, BLG-04~06, FR-016~FR-024). */
class JoinRequestIntegrationTest extends BlogTestBase {

    private Res apply(Member who, long blogId) throws Exception {
        return postAs(who, "/api/blogs/" + blogId + "/join-requests", null);
    }

    @Test
    void 자유_참여는_바로_멤버가_된다() throws Exception {
        Member owner = member("장");
        Member m = member("멤");
        long blog = createBlog(owner, "free-blog", "PUBLIC", "FREE");
        Res r = apply(m, blog);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.json().get("status").asText()).isEqualTo("APPROVED");
        assertThat(role(blog, m.id())).isEqualTo("MEMBER");
        assertThat(jdbc.queryForObject("SELECT member_count FROM blogs WHERE id = ?", Integer.class, blog)).isEqualTo(2);
        Res mine = getAs(m, "/api/me/blogs");
        assertThat(mine.json().get("joined").get(0).get("address").asText()).isEqualTo("free-blog");
    }

    @Test
    void 승인제는_대기하고_블로그장과_승인_권한_부블로그장에게_알림이_간다() throws Exception {
        Member owner = member("장2");
        Member manager = member("부");
        Member plainManager = member("부2");
        Member m = member("신청");
        long blog = createBlog(owner, "approval-blog", "PUBLIC", "APPROVAL");
        addMember(blog, manager.id(), "MEMBER");
        setManagerPerms(blog, manager.id(), false, true, false);
        addMember(blog, plainManager.id(), "MEMBER");
        setManagerPerms(blog, plainManager.id(), true, false, false);

        Res r = apply(m, blog);
        assertThat(r.json().get("status").asText()).isEqualTo("PENDING");
        assertThat(notificationsOf(owner.id())).containsExactly("JOIN_REQUESTED");
        assertThat(notificationsOf(manager.id())).containsExactly("JOIN_REQUESTED");
        assertThat(notificationsOf(plainManager.id())).isEmpty();

        Res again = apply(m, blog);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("JOIN_PENDING_EXISTS");

        Res list = getAs(manager, "/api/blogs/" + blog + "/join-requests");
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.json().get("items").get(0).get("nickname").asText()).isEqualTo("신청");
        assertThat(getAs(plainManager, "/api/blogs/" + blog + "/join-requests").status()).isEqualTo(403);

        long requestId = r.json().get("id").asLong();
        Res ok = postAs(manager, "/api/blogs/" + blog + "/join-requests/" + requestId + "/approval", null);
        assertThat(ok.status()).isEqualTo(200);
        assertThat(role(blog, m.id())).isEqualTo("MEMBER");
        assertThat(notificationsOf(m.id())).containsExactly("JOIN_APPROVED");
        assertThat(postAs(owner, "/api/blogs/" + blog + "/join-requests/" + requestId + "/rejection", null).code())
                .isEqualTo("JOIN_NOT_PENDING");
        assertThat(apply(m, blog).code()).isEqualTo("ALREADY_MEMBER");
        assertThat(apply(owner, blog).code()).isEqualTo("ALREADY_OWNER");
    }

    @Test
    void 거절되면_7일_뒤에야_다시_신청한다() throws Exception {
        Member owner = member("장3");
        Member m = member("거절");
        long blog = createBlog(owner, "reject-blog", "PUBLIC", "APPROVAL");
        long id = apply(m, blog).json().get("id").asLong();
        postAs(owner, "/api/blogs/" + blog + "/join-requests/" + id + "/rejection", null);
        assertThat(notificationsOf(m.id())).containsExactly("JOIN_REJECTED");

        clock.advance(Duration.ofDays(6));
        m = relogin(m);
        Res tooSoon = apply(m, blog);
        assertThat(tooSoon.code()).isEqualTo("JOIN_REAPPLY_TOO_SOON");
        assertThat(tooSoon.json().has("reapplyAt")).isTrue();
        clock.advance(Duration.ofDays(1).plusMinutes(1));
        m = relogin(m);
        assertThat(apply(m, blog).json().get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void 대기_중_신청은_취소하고_바로_다시_신청할_수_있다() throws Exception {
        Member owner = member("장4");
        Member m = member("취소");
        long blog = createBlog(owner, "cancel-blog", "PUBLIC", "APPROVAL");
        long id = apply(m, blog).json().get("id").asLong();
        assertThat(deleteAs(owner, "/api/me/join-requests/" + id).status()).isEqualTo(404);
        assertThat(deleteAs(m, "/api/me/join-requests/" + id).status()).isEqualTo(204);
        assertThat(getAs(owner, "/api/blogs/" + blog + "/join-requests").json().get("items").size()).isZero();
        assertThat(apply(m, blog).json().get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void 블로그장이_차단한_회원은_자동_거절된다() throws Exception {
        Member owner = member("장5");
        Member blocked = member("차단됨");
        long blog = createBlog(owner, "block-blog", "PUBLIC", "FREE");
        jdbc.update("INSERT INTO user_blocks (blocker_id, blocked_id) VALUES (?, ?)", owner.id(), blocked.id());
        Res r = apply(blocked, blog);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.json().get("status").asText()).isEqualTo("REJECTED");
        assertThat(role(blog, blocked.id())).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM blog_join_requests WHERE user_id = ?", String.class,
                blocked.id())).isEqualTo("AUTO_REJECTED");
        assertThat(notificationsOf(blocked.id())).containsExactly("JOIN_REJECTED");
    }

    @Test
    void 메인_관리자와_비회원은_신청할_수_없고_비공개는_없는_블로그처럼_보인다() throws Exception {
        Member owner = member("장6");
        long blog = createBlog(owner, "admin-try", "PUBLIC", "FREE");
        Member admin = admin("관리자1");
        assertThat(apply(admin, blog).code()).isEqualTo("ADMIN_NOT_ALLOWED");
        assertThat(apply(null, blog).status()).isEqualTo(401);
        long priv = createBlog(owner, "priv-try", "PRIVATE", "FREE");
        assertThat(apply(member("밖"), priv).status()).isEqualTo(404);
    }

    @Test
    void 내_블로그는_운영하는_곳과_참여한_곳을_나눈다() throws Exception {
        Member me = member("나");
        createBlog(me, "mine-1", "PUBLIC", "FREE");
        createBlog(me, "mine-2", "PRIVATE", "FREE");
        for (int i = 1; i <= 3; i++) {
            long other = createBlog(member("남" + i), "other-" + i, "PUBLIC", "FREE");
            apply(me, other);
        }
        Res r = getAs(me, "/api/me/blogs");
        assertThat(r.json().get("owned").size()).isEqualTo(2);
        assertThat(r.json().get("joined").size()).isEqualTo(3);
    }
}
