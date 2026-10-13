package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** US6 블로그장 위임 (T053, T058, BLG-08, FR-066~FR-072, FR-082). */
class DelegationIntegrationTest extends BlogTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    com.mainblog.blog.application.OwnerSuccessionService successionService;

    private Res request(Member owner, long blog, long toUserId) throws Exception {
        return postAs(owner, "/api/blogs/" + blog + "/delegation", "{\"toUserId\":" + toUserId + "}");
    }

    @Test
    void 수락하면_블로그장이_바뀌고_이전_블로그장은_멤버로_남는다() throws Exception {
        Member y = member("와이");
        long blog = createBlog(y, "dele-accept", "PUBLIC", "FREE");
        Member z = member("제트");
        addMember(blog, z.id(), "MEMBER");
        assertThat(getAs(y, "/api/blogs/address/dele-accept").json().get("canDelegate").asBoolean()).isTrue();

        Res r = request(y, blog, z.id());
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.json().get("status").asText()).isEqualTo("PENDING");
        assertThat(notificationsOf(z.id())).containsExactly("DELEGATION_REQUESTED");
        assertThat(getAs(z, "/api/blogs/" + blog + "/delegation").json().get("toUserId").asLong()).isEqualTo(z.id());

        Res ok = postAs(z, "/api/blogs/" + blog + "/delegation/acceptance", null);
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.json().get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(role(blog, z.id())).isEqualTo("OWNER");
        assertThat(role(blog, y.id())).isEqualTo("MEMBER");
        assertThat(notificationsOf(y.id())).containsExactly("DELEGATION_ACCEPTED");
        // 같은 쿠키로 바로 권한이 바뀐다
        assertThat(patchAs(y, "/api/blogs/" + blog, "{\"name\":\"x\"}").status()).isEqualTo(403);
        assertThat(patchAs(z, "/api/blogs/" + blog, "{\"name\":\"새 주인\"}").status()).isEqualTo(200);
        assertThat(getAs(z, "/api/blogs/" + blog + "/delegation").status()).isEqualTo(204);
    }

    @Test
    void 거절하면_그대로이고_블로그장에게_알린다() throws Exception {
        Member y = member("와이2");
        long blog = createBlog(y, "dele-reject", "PUBLIC", "FREE");
        Member z = member("제트2");
        addMember(blog, z.id(), "MEMBER");
        request(y, blog, z.id());
        Member other = member("남");
        addMember(blog, other.id(), "MEMBER");
        assertThat(postAs(other, "/api/blogs/" + blog + "/delegation/acceptance", null).status()).isEqualTo(403);
        assertThat(getAs(other, "/api/blogs/" + blog + "/delegation").status()).isEqualTo(403);

        assertThat(postAs(z, "/api/blogs/" + blog + "/delegation/rejection", null).json().get("status").asText())
                .isEqualTo("REJECTED");
        assertThat(role(blog, y.id())).isEqualTo("OWNER");
        assertThat(notificationsOf(y.id())).containsExactly("DELEGATION_REJECTED");
        assertThat(postAs(z, "/api/blogs/" + blog + "/delegation/acceptance", null).code())
                .isEqualTo("DELEGATION_NOT_PENDING");
    }

    @Test
    void 대기_중이면_두_번째_요청은_409이고_대상은_멤버여야_한다() throws Exception {
        Member y = member("와이3");
        long blog = createBlog(y, "dele-one", "PUBLIC", "FREE");
        Member z = member("제트3");
        Member w = member("더블유");
        addMember(blog, z.id(), "MEMBER");
        addMember(blog, w.id(), "MANAGER");
        assertThat(request(y, blog, member("밖").id()).status()).isEqualTo(404);
        assertThat(request(y, blog, y.id()).status()).isEqualTo(404);
        assertThat(request(z, blog, w.id()).status()).isEqualTo(403);
        request(y, blog, z.id());
        assertThat(request(y, blog, w.id()).code()).isEqualTo("DELEGATION_PENDING_EXISTS");
    }

    @Test
    void 칠일_무응답이면_04시에_자동_취소되고_그_전에도_만료면_수락할_수_없다() throws Exception {
        Member y = member("와이4");
        long blog = createBlog(y, "dele-expire", "PUBLIC", "FREE");
        Member z = member("제트4");
        addMember(blog, z.id(), "MEMBER");
        request(y, blog, z.id());

        clock.advance(Duration.ofDays(7).plusMinutes(1)); // 04:00 처리 전
        z = relogin(z);
        assertThat(postAs(z, "/api/blogs/" + blog + "/delegation/acceptance", null).code())
                .isEqualTo("DELEGATION_NOT_PENDING");
        assertThat(getAs(z, "/api/blogs/" + blog + "/delegation").status()).isEqualTo(204);
        runDaily();
        runDaily();
        assertThat(jdbc.queryForObject("SELECT status FROM blog_delegations WHERE blog_id = ?", String.class, blog))
                .isEqualTo("AUTO_CANCELED");
        assertThat(notificationsOf(y.id())).containsExactly("DELEGATION_CANCELED");
        assertThat(role(blog, y.id())).isEqualTo("OWNER");
    }

    @Test
    void 폐쇄_예정이면_위임할_수_없고_철회하면_다시_할_수_있다() throws Exception {
        Member y = member("와이5");
        long blog = createBlog(y, "dele-closing", "PUBLIC", "FREE");
        Member z = member("제트5");
        addMember(blog, z.id(), "MEMBER");
        postAs(y, "/api/blogs/" + blog + "/closing", null);
        assertThat(getAs(y, "/api/blogs/address/dele-closing").json().get("canDelegate").asBoolean()).isFalse();
        assertThat(request(y, blog, z.id()).code()).isEqualTo("BLOG_CLOSING");
        deleteAs(y, "/api/blogs/" + blog + "/closing");
        assertThat(getAs(y, "/api/blogs/address/dele-closing").json().get("canDelegate").asBoolean()).isTrue();
        assertThat(request(y, blog, z.id()).status()).isEqualTo(201);
    }

    @Test
    void 위임할_멤버가_없으면_위임_버튼이_없다() throws Exception {
        Member y = member("와이6");
        createBlog(y, "dele-alone", "PUBLIC", "FREE");
        assertThat(getAs(y, "/api/blogs/address/dele-alone").json().get("canDelegate").asBoolean()).isFalse();
    }

    @Test
    void 부블로그장이_위임받으면_부블로그장_권한은_없어지고_다른_부블로그장은_그대로다() throws Exception {
        Member y = member("와이7");
        long blog = createBlog(y, "dele-manager", "PUBLIC", "FREE");
        Member z = member("제트7");
        Member w = member("더블유7");
        addMember(blog, z.id(), "MEMBER");
        addMember(blog, w.id(), "MEMBER");
        setManagerPerms(blog, z.id(), true, true, true);
        setManagerPerms(blog, w.id(), false, true, false);
        request(y, blog, z.id());
        postAs(z, "/api/blogs/" + blog + "/delegation/acceptance", null);

        var row = jdbc.queryForMap("SELECT role, manager_since, perm_blog_info, perm_member_manage, perm_post_manage "
                + "FROM blog_members WHERE blog_id = ? AND user_id = ?", blog, z.id());
        assertThat(row.get("role")).isEqualTo("OWNER");
        assertThat(row.get("manager_since")).isNull();
        assertThat(row.get("perm_member_manage")).isEqualTo(false);
        assertThat(role(blog, w.id())).isEqualTo("MANAGER");
        assertThat(jdbc.queryForObject("SELECT perm_member_manage FROM blog_members WHERE blog_id = ? AND user_id = ?",
                Boolean.class, blog, w.id())).isTrue();
    }

    @Test
    void 블로그장_권한_박탈_뒤_가장_먼저_된_부블로그장이_승계한다() throws Exception {
        Member y = member("와이8");
        long blog = createBlog(y, "revoke-pass", "PUBLIC", "FREE");
        Member early = member("먼저");
        Member late = member("나중");
        addMember(blog, late.id(), "MEMBER");
        addMember(blog, early.id(), "MEMBER");
        setManagerPerms(blog, early.id(), true, false, false);
        clock.advance(Duration.ofMinutes(1));
        setManagerPerms(blog, late.id(), true, true, true);

        var result = successionService.revokeOwner(blog, null);
        assertThat(result.newOwnerId()).isEqualTo(early.id());
        assertThat(role(blog, early.id())).isEqualTo("OWNER");
        assertThat(role(blog, y.id())).isEqualTo("MEMBER");
        assertThat(role(blog, late.id())).isEqualTo("MANAGER");
    }

    @Test
    void 부블로그장이_없으면_권한_박탈_폐쇄를_예약하고_철회할_사람이_없다() throws Exception {
        Member y = member("와이9");
        long blog = createBlog(y, "revoke-close", "PUBLIC", "FREE");
        Member m = member("멤버9");
        addMember(blog, m.id(), "MEMBER");

        var result = successionService.revokeOwner(blog, null);
        assertThat(result.newOwnerId()).isNull();
        assertThat(result.closeScheduledAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT close_reason FROM blogs WHERE id = ?", String.class, blog))
                .isEqualTo("OWNER_REVOKED");
        assertThat(role(blog, y.id())).isEqualTo("MEMBER");
        // 박탈된 사람은 폐쇄 예정 알림에서 빠진다(005 FR-024, US3-5)
        assertThat(notificationsOf(y.id())).doesNotContain("BLOG_CLOSING");
        assertThat(jdbc.queryForObject("SELECT content FROM notifications WHERE recipient_id = ?", String.class,
                m.id())).contains("블로그장 권한 박탈로 인한 폐쇄 조치");
        assertThat(deleteAs(y, "/api/blogs/" + blog + "/closing").status()).isEqualTo(403);
    }
}
