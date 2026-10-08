package com.mainblog.blog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mainblog.batch.DailyCleanupJob;
import com.mainblog.support.AuthTestSupport;
import com.mainblog.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 002 블로그 통합 테스트 도우미(004 BoardTestBase도 확장한다). 회원을 만들고 로그인 쿠키로 API를 부른다. */
public abstract class BlogTestBase extends IntegrationTestBase {

    protected static final String PW = "Passw0rd!";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected DailyCleanupJob dailyCleanupJob;

    /** 로그인한 회원. extraCookies는 공유 링크 쿠키 등. */
    protected record Member(long id, String email, LoginCookies login, List<Cookie> extraCookies) {
        public Cookie[] cookies() {
            List<Cookie> all = new ArrayList<>(Arrays.asList(login.cookies()));
            all.addAll(extraCookies);
            return all.toArray(Cookie[]::new);
        }
    }

    /** 전화번호가 겹치지 않는 회원(블랙리스트 비교가 섞이지 않게). */
    protected Member member(String nickname) throws Exception {
        int n = SEQ.incrementAndGet();
        String email = "u" + n + "@test.com";
        String phone = "010" + String.format("%08d", 10000000 + n);
        long id = createUser(email, PW, nickname, "회원" + (char) ('가' + (n % 50)), phone);
        return new Member(id, email, loginAs(email, PW, false), new ArrayList<>());
    }

    /** 시계를 크게 옮긴 뒤 만료된 액세스 토큰 대신 다시 로그인한다. */
    protected Member relogin(Member m) throws Exception {
        return new Member(m.id(), m.email(), loginAs(m.email(), PW, false), m.extraCookies());
    }

    protected Member admin(String nickname) throws Exception {
        Member m = member(nickname);
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", m.id());
        return m;
    }

    protected Res call(Member who, MockHttpServletRequestBuilder builder) throws Exception {
        MockHttpServletRequestBuilder b = AuthTestSupport.withCsrf(builder);
        if (who != null) {
            b.cookie(who.cookies());
        }
        MockHttpServletResponse res = mvc.perform(b).andReturn().getResponse();
        return new Res(res.getStatus(), res.getContentAsString(), res);
    }

    protected Res getAs(Member who, String url) throws Exception {
        return call(who, get(java.net.URI.create(url))); // 이미 인코딩된 주소를 다시 인코딩하지 않는다
    }

    protected Res postAs(Member who, String url, String json) throws Exception {
        return call(who, post(url).contentType(MediaType.APPLICATION_JSON).content(json == null ? "{}" : json));
    }

    protected Res putAs(Member who, String url, String json) throws Exception {
        return call(who, put(url).contentType(MediaType.APPLICATION_JSON).content(json == null ? "{}" : json));
    }

    protected Res patchAs(Member who, String url, String json) throws Exception {
        return call(who, patch(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected Res deleteAs(Member who, String url) throws Exception {
        return call(who, delete(url));
    }

    /** API로 블로그를 만들고 번호를 돌려준다. */
    protected long createBlog(Member owner, String address, String visibility, String joinPolicy) throws Exception {
        Res r = postAs(owner, "/api/blogs", blogJson("블로그 " + address, address, visibility, joinPolicy));
        if (r.status() != 201) {
            throw new AssertionError("블로그 생성 실패: " + r.status() + " " + r.body());
        }
        return r.json().get("id").asLong();
    }

    protected static String blogJson(String name, String address, String visibility, String joinPolicy) {
        return "{\"name\":\"%s\",\"address\":\"%s\",\"visibility\":\"%s\",\"joinPolicy\":\"%s\"}"
                .formatted(name, address, visibility, joinPolicy);
    }

    /** 04:00 처리를 지금 시계 시각으로 한 번 돌린다. */
    protected void runDaily() {
        dailyCleanupJob.runOnce();
    }

    /** 강퇴 기록과 블랙리스트 행을 직접 넣는다(해시 값은 비교하지 않는 테스트용). 블랙리스트 번호를 돌려준다. */
    protected long insertBlacklist(long blogId, long userId, long handledBy) {
        jdbc.update("INSERT INTO blog_sanctions (blog_id, user_id, handled_by, sanction_type, starts_at) "
                + "VALUES (?, ?, ?, 'KICK', ?)", blogId, userId, handledBy, clock.now());
        Long sanctionId = jdbc.queryForObject("SELECT MAX(id) FROM blog_sanctions", Long.class);
        jdbc.update("INSERT INTO blog_blacklists (sanction_id, blog_id, name_hash, email_hash, phone_hash) "
                + "VALUES (?, ?, 'n', 'e', 'p')", sanctionId, blogId);
        return jdbc.queryForObject("SELECT MAX(id) FROM blog_blacklists", Long.class);
    }

    protected long insertAppeal(long blacklistId, long userId) {
        jdbc.update("INSERT INTO blacklist_appeals (blacklist_id, user_id, content, name_matched, phone_matched) "
                + "VALUES (?, ?, '풀어 주세요', TRUE, FALSE)", blacklistId, userId);
        return jdbc.queryForObject("SELECT MAX(id) FROM blacklist_appeals", Long.class);
    }

    protected String role(long blogId, long userId) {
        List<String> r = jdbc.queryForList("SELECT role FROM blog_members WHERE blog_id = ? AND user_id = ?",
                String.class, blogId, userId);
        return r.isEmpty() ? null : r.get(0);
    }

    protected final class Res {
        private final int status;
        private final String body;
        private final MockHttpServletResponse raw;

        public Res(int status, String body, MockHttpServletResponse raw) {
            this.status = status;
            this.body = body;
            this.raw = raw;
        }

        public int status() {
            return status;
        }

        public String body() {
            return body;
        }

        public MockHttpServletResponse raw() {
            return raw;
        }

        public JsonNode json() throws Exception {
            return objectMapper.readTree(body);
        }

        public String code() throws Exception {
            JsonNode c = json().get("code");
            return c == null ? null : c.asText();
        }

        @Override
        public String toString() {
            return status + " " + body;
        }
    }
}
