package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.yaml.snakeyaml.Yaml;

/**
 * 005 보안 점검 (T054, SC-002, SC-003, FR-003, FR-007, FR-009, SEC-11, 4.4, D-22).
 * - 계약의 모든 /api/admin/** 경로는 비회원 401, 관리자가 아닌 회원(블로그장·부블로그장 포함)과 권한 회수된 관리자는 403 ADMIN_ONLY.
 * - 상태를 바꾸는 요청은 CSRF 토큰이 없으면 403. "전체 보기"를 뺀 관리자 응답과 로그에 이메일·전화번호 원문이 없다.
 */
@ExtendWith(OutputCaptureExtension.class)
class AdminSecurityTest extends AdminTestBase {

    static final Path ADMIN_CONTRACT = Path.of("specs/005-admin-moderation/contracts/admin-api.yaml");
    static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete");

    @SuppressWarnings("unchecked")
    private static Set<String> operations() throws Exception {
        try (InputStream in = Files.newInputStream(ADMIN_CONTRACT)) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) root.get("paths");
            Set<String> ops = new TreeSet<>();
            paths.forEach((path, item) -> item.keySet().stream().filter(METHODS::contains)
                    .forEach(m -> ops.add(m.toUpperCase(Locale.ROOT) + " " + path.replaceAll("\\{[^}]+}", "1"))));
            return ops;
        }
    }

    private static MockHttpServletRequestBuilder request(String op) {
        String method = op.substring(0, op.indexOf(' '));
        String url = op.substring(op.indexOf(' ') + 1);
        return switch (method) {
            case "GET" -> get(url);
            case "POST" -> post(url).contentType(MediaType.APPLICATION_JSON).content("{}");
            case "PUT" -> put(url).contentType(MediaType.APPLICATION_JSON).content("{}");
            default -> delete(url);
        };
    }

    @Test
    void 계약의_모든_관리자_API는_비회원_401_관리자_아니면_403() throws Exception {
        BlogFixture b = blog("sec-admin-all", "PUBLIC");
        Member manager = member("보안부장");
        addMember(b.blogId(), manager.id(), "MANAGER");
        Member user = member("보안회원");
        Member revoked = admin("회수관리자");
        jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", revoked.id());

        Set<String> ops = operations();
        assertThat(ops).hasSize(28); // D-104로 강제 폐쇄 철회(DELETE force-close) 추가
        for (String op : ops) {
            Res guest = call(null, request(op));
            assertThat(guest.status()).as("비회원 " + op).isEqualTo(401);
            for (Member who : List.of(user, b.owner(), manager, revoked)) {
                Res r = call(who, request(op));
                assertThat(r.status()).as(who.email() + " " + op).isEqualTo(403);
                assertThat(r.code()).as(op).isEqualTo("ADMIN_ONLY");
                assertThat(r.body()).doesNotContain(who.email());
            }
        }
        assertThat(actionTypes()).isEmpty();
    }

    @Test
    void 상태를_바꾸는_관리자_요청은_CSRF_토큰이_없으면_403() throws Exception {
        Member admin = admin("CSRF관리자");
        BlogFixture b = blog("sec-admin-csrf", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "본문");
        List<MockHttpServletRequestBuilder> writes = new ArrayList<>();
        writes.add(put("/api/admin/blogs/" + b.blogId() + "/hidden"));
        writes.add(post("/api/admin/blogs/" + b.blogId() + "/force-close"));
        writes.add(delete("/api/admin/blogs/" + b.blogId() + "/force-close"));
        writes.add(post("/api/admin/blogs/" + b.blogId() + "/owner-warnings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerId\":" + b.owner().id() + ",\"reason\":\"x\"}"));
        writes.add(post("/api/admin/blogs/" + b.blogId() + "/owner-revocation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerId\":" + b.owner().id() + "}"));
        writes.add(put("/api/admin/posts/" + post + "/hidden"));
        writes.add(delete("/api/admin/posts/" + post));
        writes.add(post("/api/admin/members/" + b.owner().id() + "/privacy-view"));
        for (MockHttpServletRequestBuilder w : writes) {
            MockHttpServletResponse res = mvc.perform(w.cookie(admin.cookies())).andReturn().getResponse();
            assertThat(res.getStatus()).isEqualTo(403);
        }
        assertThat(actionTypes()).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM blogs WHERE id = ? AND hidden = FALSE AND status = 'ACTIVE'",
                b.blogId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM posts WHERE id = ? AND status = 'VISIBLE'", post)).isEqualTo(1);
    }

    @Test
    void 전체_보기를_뺀_관리자_응답과_로그에_이메일_전화번호_원문과_비밀번호가_없다(CapturedOutput output) throws Exception {
        Member admin = admin("개인정보관리자");
        long secretId = createUser("admin-secret@test.com", PW, "비밀블로그장", "홍비밀", "01077778888");
        Member secret = new Member(secretId, "admin-secret@test.com",
                loginAs("admin-secret@test.com", PW, false), new ArrayList<>());
        long blogId = createBlog(secret, "sec-admin-pii", "PUBLIC", "FREE");
        long post = writePost(secret, blogId, "비밀 글", "본문");
        Member fan = member("보안팬");
        long comment = postAs(fan, "/api/posts/" + post + "/comments", json(Map.of("content", "댓글")))
                .json().get("id").asLong();
        long report = insertAdminReport(blogId, fan.id(), "BLOG", blogId);
        long userReport = insertAdminReport(null, fan.id(), "USER", secretId);

        StringBuilder bodies = new StringBuilder();
        for (String url : List.of(
                "/api/admin/members?type=NICKNAME&q=" + java.net.URLEncoder.encode("비밀", "UTF-8"),
                "/api/admin/members?type=EMAIL&q=admin-secret@test.com",
                "/api/admin/members/" + secretId,
                "/api/admin/blogs",
                "/api/admin/blogs/" + blogId,
                "/api/admin/blogs/" + blogId + "/owner-warnings",
                "/api/admin/reports",
                "/api/admin/reports/" + report,
                "/api/admin/reports/" + userReport,
                "/api/admin/posts",
                "/api/admin/posts/" + post,
                "/api/admin/comments?postId=" + post,
                "/api/admin/stats")) {
            Res r = getAs(admin, url);
            assertThat(r.status()).as(url).isEqualTo(200);
            bodies.append(r.body());
        }
        bodies.append(postAs(admin, "/api/admin/blogs/" + blogId + "/owner-warnings",
                json(Map.of("ownerId", secretId, "reason", "보안 경고"))).body());
        bodies.append(postAs(admin, "/api/admin/reports/" + userReport + "/resolution", "{\"result\":\"NONE\"}").body());
        bodies.append(postAs(admin, "/api/admin/blogs/" + blogId + "/owner-revocation",
                "{\"ownerId\":" + secretId + "}").body());
        putAs(admin, "/api/admin/comments/" + comment + "/hidden", null);
        bodies.append(getAs(admin, "/api/admin/actions").body());

        String all = bodies.toString();
        assertThat(all).contains("비밀블로그장");
        for (String s : List.of("admin-secret@test.com", "01077778888", "010-7777-8888")) {
            assertThat(all).as("응답").doesNotContain(s);
        }
        assertThat(all.toLowerCase(Locale.ROOT)).doesNotContain("password");

        // "전체 보기"만 원문을 주고, 누를 때마다 기록이 하나씩 남는다
        Res view = postAs(admin, "/api/admin/members/" + secretId + "/privacy-view", null);
        assertThat(view.body()).contains("admin-secret@test.com");
        postAs(admin, "/api/admin/members/" + secretId + "/privacy-view", null);
        assertThat(actions("PRIVACY_VIEW")).hasSize(2);
        assertThat(count("SELECT COUNT(*) FROM admin_actions WHERE memo LIKE ? OR memo LIKE ?",
                "%admin-secret@test.com%", "%01077778888%")).isZero();

        for (String s : List.of("admin-secret@test.com", "01077778888", "010-7777-8888")) {
            assertThat(output.getAll()).as("로그").doesNotContain(s);
        }
    }

    @Test
    void 관리자_화면_HTML은_static에_없고_관리자가_아니면_받을_수_없다() throws Exception {
        assertThat(new ClassPathResource("static/admin/index.html").exists()).isFalse();
        assertThat(new ClassPathResource("admin/index.html").exists()).isTrue();
        assertThat(getAs(member("화면회원"), "/admin/index.html").status()).isEqualTo(404);
        assertThat(getAs(null, "/admin/index.html").status()).isEqualTo(302);
    }
}
