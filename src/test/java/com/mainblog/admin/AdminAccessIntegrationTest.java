package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.blog.BlogTestBase.Member;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 관리자만 들어오는 틀 (005 T012, SC-002, FR-001~FR-003, D-103 #1, research R-02·R-03). */
class AdminAccessIntegrationTest extends AdminTestBase {

    static final String MEMBERS = "/api/admin/members?type=NICKNAME&q=a";

    @Test
    void 관리자_화면은_비로그인은_로그인으로_회원과_블로그장은_404_관리자는_200() throws Exception {
        Res guest = getAs(null, "/admin");
        assertThat(guest.status()).isEqualTo(302);
        assertThat(guest.raw().getHeader("Location")).isEqualTo("/login.html?next=/admin");

        Member user = member("회원1");
        assertThat(getAs(user, "/admin").status()).isEqualTo(404);
        BlogFixture b = blog("owner-blog", "PUBLIC");
        Res owner = getAs(b.owner(), "/admin");
        assertThat(owner.status()).isEqualTo(404);
        assertThat(owner.body()).doesNotContain("admin-tabs");

        Member admin = admin("관리자1");
        Res ok = getAs(admin, "/admin");
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.raw().getContentType()).startsWith(MediaType.TEXT_HTML_VALUE);
        assertThat(ok.body()).contains("admin-tabs");
        assertThat(ok.raw().getHeader("Cache-Control")).contains("no-store");
        assertThat(getAs(admin, "/admin/members").status()).isEqualTo(200);
    }

    @Test
    void 관리자_화면_HTML은_static에서_직접_받을_수_없다() throws Exception {
        Member user = member("회원2");
        assertThat(getAs(user, "/admin/index.html").status()).isEqualTo(404);
        assertThat(getAs(null, "/index.html").status()).isEqualTo(200);
    }

    @Test
    void 관리자_API는_비회원_401_회원_403_ADMIN_ONLY이고_본문에_회원정보가_없다() throws Exception {
        Res guest = getAs(null, MEMBERS);
        assertThat(guest.status()).isEqualTo(401);
        assertThat(guest.code()).isEqualTo("UNAUTHENTICATED");

        Member user = member("회원3");
        Res denied = getAs(user, MEMBERS);
        assertThat(denied.status()).isEqualTo(403);
        assertThat(denied.code()).isEqualTo("ADMIN_ONLY");
        assertThat(denied.body()).doesNotContain(user.email()).doesNotContain("회원3");
    }

    @Test
    void 관리자_role을_USER로_되돌리면_다음_요청부터_403() throws Exception {
        Member admin = admin("관리자2");
        assertThat(getAs(admin, MEMBERS).status()).isEqualTo(200);
        jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", admin.id());
        Res r = getAs(admin, MEMBERS);
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.code()).isEqualTo("ADMIN_ONLY");
        assertThat(getAs(admin, "/admin").status()).isEqualTo(404);
    }

    @Test
    void CSRF가_없는_관리자_요청은_403이다() throws Exception {
        Member admin = admin("관리자3");
        var res = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/blogs/1/hidden").cookie(admin.cookies()))
                .andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).doesNotContain("ADMIN_ONLY");
    }
}
