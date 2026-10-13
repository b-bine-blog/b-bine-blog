package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 관리자 회원 조회와 "전체 보기" (005 T013, US1-1~5, SC-003, FR-005~FR-009, D-103 #2~#5). */
class AdminMemberIntegrationTest extends AdminTestBase {

    @Test
    void 닉네임_일부와_이메일_전체로_찾고_탈퇴회원과_관리자는_빠진다() throws Exception {
        Member admin = admin("관리자1");
        Member a = member("초록나무");
        Member b = member("초록바다");
        Member gone = member("초록탈퇴");
        withdraw(gone.id());
        admin("초록관리");

        JsonNode byNick = getAs(admin, "/api/admin/members?type=NICKNAME&q=" + enc("초록")).json();
        assertThat(byNick.get("totalElements").asInt()).isEqualTo(2);
        assertThat(byNick.get("items").findValuesAsText("nickname")).containsExactlyInAnyOrder("초록나무", "초록바다");
        assertThat(byNick.get("items").get(0).get("maskedEmail").asText()).contains("***@test.com");
        assertThat(byNick.get("size").asInt()).isEqualTo(20);

        JsonNode byEmail = getAs(admin, "/api/admin/members?type=EMAIL&q=" + a.email().toUpperCase()).json();
        assertThat(byEmail.get("items").findValuesAsText("nickname")).containsExactly("초록나무");
        String partial = b.email().substring(0, b.email().indexOf('@'));
        assertThat(getAs(admin, "/api/admin/members?type=EMAIL&q=" + partial).json().get("totalElements").asInt())
                .isZero();
        assertThat(getAs(admin, "/api/admin/members?type=EMAIL&q=" + gone.email()).json().get("totalElements")
                .asInt()).isZero();
    }

    @Test
    void 닉네임의_퍼센트와_밑줄은_글자_그대로_찾는다() throws Exception {
        Member admin = admin("관리자9");
        member("a_b");
        member("axb");
        JsonNode r = getAs(admin, "/api/admin/members?type=NICKNAME&q=a_").json();
        assertThat(r.get("items").findValuesAsText("nickname")).containsExactly("a_b");
        assertThat(getAs(admin, "/api/admin/members?type=NICKNAME&q=%25").json().get("totalElements").asInt())
                .isZero();
    }

    @Test
    void 한_페이지는_10_20_50만_받는다() throws Exception {
        Member admin = admin("관리자2");
        for (int size : new int[] {10, 20, 50}) {
            assertThat(getAs(admin, "/api/admin/members?type=NICKNAME&q=a&size=" + size).status()).isEqualTo(200);
        }
        Res bad = getAs(admin, "/api/admin/members?type=NICKNAME&q=a&size=30");
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(getAs(admin, "/api/admin/members?type=NAME&q=a").status()).isEqualTo(400);
        assertThat(getAs(admin, "/api/admin/members?type=NICKNAME&q=" + "가".repeat(13)).status()).isEqualTo(400);
    }

    @Test
    void 상세는_이메일과_전화번호를_가리고_이름은_보이며_원문과_비밀번호_칸이_없다() throws Exception {
        Member admin = admin("관리자3");
        long u = createUser("abcdef@gmail.com", PW, "가림회원", "김가림", "01012345678");
        long v = createUser("ab@x.com", PW, "짧은메일", "박짧음", "01099998888");

        Res r = getAs(admin, "/api/admin/members/" + u);
        assertThat(r.status()).isEqualTo(200);
        JsonNode d = r.json();
        assertThat(d.get("maskedEmail").asText()).isEqualTo("ab***@gmail.com");
        assertThat(d.get("maskedPhone").asText()).isEqualTo("010-****-5678");
        assertThat(d.get("name").asText()).isEqualTo("김가림");
        assertThat(d.get("nickname").asText()).isEqualTo("가림회원");
        assertThat(r.body()).doesNotContain("abcdef@gmail.com").doesNotContain("01012345678")
                .doesNotContain("1234").doesNotContain("password");
        assertThat(getAs(admin, "/api/admin/members/" + v).json().get("maskedEmail").asText()).isEqualTo("a***@x.com");
    }

    @Test
    void 상세에_비공개_블로그를_포함한_운영_참여_블로그와_블로그별_경고_이력이_있다() throws Exception {
        Member admin = admin("관리자4");
        Member o = member("운영자");
        long pub = createBlog(o, "pub-blog", "PUBLIC", "FREE");
        long priv = createBlog(o, "priv-blog", "PRIVATE", "FREE");
        BlogFixture other = blog("other-blog", "PUBLIC");
        addMember(other.blogId(), o.id(), "MEMBER");
        insertWarning(pub, o.id(), admin.id(), clock.now().minusDays(400));
        insertWarning(pub, o.id(), admin.id(), clock.now().minusDays(3));
        insertWarning(priv, o.id(), admin.id(), clock.now().minusDays(1));

        JsonNode d = getAs(admin, "/api/admin/members/" + o.id()).json();
        assertThat(d.get("blogs").findValuesAsText("role")).containsExactly("OWNER", "OWNER", "MEMBER");
        assertThat(d.get("blogs").findValuesAsText("visibility")).contains("PRIVATE");
        JsonNode warnings = d.get("ownerWarnings");
        assertThat(warnings).hasSize(2);
        JsonNode pubWarnings = warnings.get(0);
        assertThat(pubWarnings.get("blogId").asLong()).isEqualTo(pub);
        assertThat(pubWarnings.get("activeCount").asInt()).isEqualTo(1);
        assertThat(pubWarnings.get("warnings")).hasSize(2);
        assertThat(pubWarnings.get("warnings").get(1).get("counted").asBoolean()).isFalse();
        assertThat(pubWarnings.get("warnings").get(0).get("reason").asText()).isEqualTo("예전 경고");
    }

    @Test
    void 탈퇴한_회원의_상세와_전체보기는_404() throws Exception {
        Member admin = admin("관리자5");
        Member gone = member("떠난회원");
        withdraw(gone.id());
        assertThat(getAs(admin, "/api/admin/members/" + gone.id()).status()).isEqualTo(404);
        assertThat(postAs(admin, "/api/admin/members/" + gone.id() + "/privacy-view", null).status()).isEqualTo(404);
        assertThat(actionTypes()).isEmpty();
    }

    @Test
    void 전체보기는_원문을_주고_PRIVACY_VIEW를_남기며_no_store다() throws Exception {
        Member admin = admin("관리자6");
        long u = createUser("abcdef@gmail.com", PW, "원문회원", "김원문", "01012345678");

        Res r = postAs(admin, "/api/admin/members/" + u + "/privacy-view", null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().get("email").asText()).isEqualTo("abcdef@gmail.com");
        assertThat(r.json().get("phone").asText()).isEqualTo("010-1234-5678");
        assertThat(r.raw().getHeader("Cache-Control")).contains("no-store");

        assertThat(actions("PRIVACY_VIEW")).hasSize(1);
        Map<String, Object> row = actions("PRIVACY_VIEW").get(0);
        assertThat(((Number) row.get("admin_id")).longValue()).isEqualTo(admin.id());
        assertThat(row.get("target_type")).isEqualTo("USER");
        assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(u);
        assertThat(row.get("created_at")).isNotNull();
        assertThat(String.valueOf(row.get("memo"))).doesNotContain("abcdef");

        // 다시 열면 다시 가린 값(D-103 #5)
        assertThat(getAs(admin, "/api/admin/members/" + u).json().get("maskedEmail").asText())
                .isEqualTo("ab***@gmail.com");
    }

    @Test
    void 전체보기는_관리자당_1분에_30번이고_31번째는_429() throws Exception {
        Member admin = admin("관리자7");
        long u = createUser("limit@test.com", PW, "제한회원");
        for (int i = 0; i < 30; i++) {
            assertThat(postAs(admin, "/api/admin/members/" + u + "/privacy-view", null).status()).isEqualTo(200);
        }
        Res r = postAs(admin, "/api/admin/members/" + u + "/privacy-view", null);
        assertThat(r.status()).isEqualTo(429);
        assertThat(r.code()).isEqualTo("RATE_LIMITED");
        assertThat(actions("PRIVACY_VIEW")).hasSize(30);
    }

    @Test
    void 전체보기에_CSRF가_없으면_403이다() throws Exception {
        Member admin = admin("관리자8");
        long u = createUser("csrf@test.com", PW, "보호회원");
        var res = mvc.perform(post("/api/admin/members/" + u + "/privacy-view").cookie(admin.cookies()))
                .andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).doesNotContain("csrf@test.com");
        assertThat(actionTypes()).isEmpty();
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
