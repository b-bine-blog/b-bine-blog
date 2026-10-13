package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** US8 부블로그장 지정과 멤버 정보 보기 (T066, FR-039~FR-047, SC-005). */
class ManagerIntegrationTest extends BlogTestBase {

    private Res setManager(Member actor, long blog, long userId, boolean info, boolean manage, boolean post)
            throws Exception {
        return putAs(actor, "/api/blogs/" + blog + "/managers/" + userId,
                "{\"blogInfo\":%s,\"memberManage\":%s,\"postManage\":%s}".formatted(info, manage, post));
    }

    @Test
    void 멤버_관리만_켠_부블로그장은_승인만_되고_정보_수정은_안_된다() throws Exception {
        Member owner = member("장");
        long blog = createBlog(owner, "mgr-manage", "PUBLIC", "APPROVAL");
        Member s = member("에스");
        addMember(blog, s.id(), "MEMBER");
        Res r = setManager(owner, blog, s.id(), false, true, false);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().get("role").asText()).isEqualTo("MANAGER");
        assertThat(r.json().get("managerSince").isNull()).isFalse();
        assertThat(r.json().get("permissions").get("memberManage").asBoolean()).isTrue();

        long req = postAs(member("신청"), "/api/blogs/" + blog + "/join-requests", null).json().get("id").asLong();
        assertThat(postAs(s, "/api/blogs/" + blog + "/join-requests/" + req + "/approval", null).status())
                .isEqualTo(200);
        assertThat(patchAs(s, "/api/blogs/" + blog, "{\"name\":\"바꿈\"}").status()).isEqualTo(403);
    }

    @Test
    void 체크박스마다_허용과_거부가_나뉜다() throws Exception {
        Member owner = member("장2");
        long blog = createBlog(owner, "mgr-boxes", "PUBLIC", "APPROVAL");
        Member s = member("에스2");
        addMember(blog, s.id(), "MEMBER");

        setManager(owner, blog, s.id(), true, false, false);
        assertThat(patchAs(s, "/api/blogs/" + blog, "{\"description\":\"소개\"}").status()).isEqualTo(200);
        assertThat(getAs(s, "/api/blogs/" + blog + "/members").status()).isEqualTo(403);
        assertThat(getAs(s, "/api/blogs/address/mgr-boxes").json().get("myPermissions").toString())
                .contains("BLOG_INFO").doesNotContain("MEMBER_MANAGE", "POST_MANAGE");

        String since = jdbc.queryForObject("SELECT manager_since FROM blog_members WHERE user_id = ?", String.class,
                s.id());
        clock.advance(java.time.Duration.ofHours(1));
        owner = relogin(owner);
        assertThat(setManager(owner, blog, s.id(), false, true, true).status()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT manager_since FROM blog_members WHERE user_id = ?", String.class,
                s.id())).isEqualTo(since); // 권한만 바꾸면 그대로
        s = relogin(s);
        assertThat(patchAs(s, "/api/blogs/" + blog, "{\"description\":\"또\"}").status()).isEqualTo(403);
        assertThat(getAs(s, "/api/blogs/" + blog + "/members").status()).isEqualTo(200);
        assertThat(getAs(s, "/api/blogs/address/mgr-boxes").json().get("myPermissions").toString())
                .contains("MEMBER_MANAGE", "POST_MANAGE");
        assertThat(putAs(s, "/api/blogs/" + blog + "/managers/" + s.id(), null).status()).isIn(400, 403);
    }

    @Test
    void 부블로그장은_지정_위임_폐쇄_공개_범위를_할_수_없다() throws Exception {
        Member owner = member("장3");
        long blog = createBlog(owner, "mgr-owner-only", "PUBLIC", "FREE");
        Member s = member("에스3");
        Member m = member("멤버3");
        addMember(blog, s.id(), "MEMBER");
        addMember(blog, m.id(), "MEMBER");
        setManager(owner, blog, s.id(), true, true, true);

        assertThat(setManager(s, blog, m.id(), true, true, true).status()).isEqualTo(403);
        assertThat(deleteAs(s, "/api/blogs/" + blog + "/managers/" + s.id()).status()).isEqualTo(403);
        assertThat(postAs(s, "/api/blogs/" + blog + "/delegation", "{\"toUserId\":" + m.id() + "}").status())
                .isEqualTo(403);
        assertThat(postAs(s, "/api/blogs/" + blog + "/closing", null).status()).isEqualTo(403);
        assertThat(patchAs(s, "/api/blogs/" + blog, "{\"visibility\":\"PRIVATE\"}").status()).isEqualTo(403);
        assertThat(patchAs(s, "/api/blogs/" + blog, "{\"joinPolicy\":\"APPROVAL\"}").status()).isEqualTo(403);
        assertThat(setManager(owner, blog, owner.id(), true, true, true).status()).isEqualTo(403);
        assertThat(setManager(owner, blog, member("밖").id(), true, true, true).status()).isEqualTo(404);
    }

    @Test
    void 멤버_정보는_가린_이메일과_전화번호만_응답한다() throws Exception {
        Member owner = member("장4");
        long blog = createBlog(owner, "mgr-mask", "PUBLIC", "FREE");
        long a = createUser("abcdef@gmail.com", PW, "에이", "가나다", "01012345678");
        long b = createUser("ab@x.com", PW, "비이", "라마바", "01098765432");
        addMember(blog, a, "MEMBER");
        addMember(blog, b, "MEMBER");

        Res r = getAs(owner, "/api/blogs/" + blog + "/members");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().get("totalCount").asInt()).isEqualTo(3);
        assertThat(r.json().get("items").get(0).get("role").asText()).isEqualTo("OWNER");
        String body = r.body();
        assertThat(body).contains("ab***@gmail.com", "010-****-5678", "a***@x.com", "가나다", "에이");
        assertThat(body).doesNotContain("abcdef@gmail.com", "01012345678", "1234", "ab@x.com", "01098765432",
                "password");
    }

    @Test
    void 멤버_관리_권한이_없으면_멤버_정보를_볼_수_없다() throws Exception {
        Member owner = member("장5");
        long blog = createBlog(owner, "mgr-noview", "PUBLIC", "FREE");
        Member m = member("멤버5");
        Member s = member("정보부장5");
        addMember(blog, m.id(), "MEMBER");
        addMember(blog, s.id(), "MEMBER");
        setManager(owner, blog, s.id(), true, false, true);
        assertThat(getAs(m, "/api/blogs/" + blog + "/members").status()).isEqualTo(403);
        assertThat(getAs(s, "/api/blogs/" + blog + "/members").status()).isEqualTo(403);
        assertThat(getAs(null, "/api/blogs/" + blog + "/members").status()).isEqualTo(401);
    }

    @Test
    void 해제하면_다음_요청부터_일반_멤버_권한만_갖는다() throws Exception {
        Member owner = member("장6");
        long blog = createBlog(owner, "mgr-remove", "PUBLIC", "FREE");
        Member s = member("에스6");
        addMember(blog, s.id(), "MEMBER");
        setManager(owner, blog, s.id(), true, true, true);
        assertThat(getAs(s, "/api/blogs/" + blog + "/members").status()).isEqualTo(200);

        assertThat(deleteAs(owner, "/api/blogs/" + blog + "/managers/" + s.id()).status()).isEqualTo(204);
        assertThat(getAs(s, "/api/blogs/" + blog + "/members").status()).isEqualTo(403);
        assertThat(role(blog, s.id())).isEqualTo("MEMBER");
        assertThat(jdbc.queryForObject("SELECT manager_since FROM blog_members WHERE user_id = ?", Object.class,
                s.id())).isNull();
        assertThat(deleteAs(owner, "/api/blogs/" + blog + "/managers/" + s.id()).status()).isEqualTo(404);
    }
}
