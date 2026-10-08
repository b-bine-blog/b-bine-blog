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

/** 002 블로그 통합 테스트 도우미. 회원을 만들고 로그인 쿠키로 API를 부른다. */
abstract class BlogTestBase extends IntegrationTestBase {

    static final String PW = "Passw0rd!";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DailyCleanupJob dailyCleanupJob;

    /** 로그인한 회원. extraCookies는 공유 링크 쿠키 등. */
    record Member(long id, String email, LoginCookies login, List<Cookie> extraCookies) {
        Cookie[] cookies() {
            List<Cookie> all = new ArrayList<>(Arrays.asList(login.cookies()));
            all.addAll(extraCookies);
            return all.toArray(Cookie[]::new);
        }
    }

    /** 전화번호가 겹치지 않는 회원(블랙리스트 비교가 섞이지 않게). */
    Member member(String nickname) throws Exception {
        int n = SEQ.incrementAndGet();
        String email = "u" + n + "@test.com";
        String phone = "010" + String.format("%08d", 10000000 + n);
        long id = createUser(email, PW, nickname, "회원" + (char) ('가' + (n % 50)), phone);
        return new Member(id, email, loginAs(email, PW, false), new ArrayList<>());
    }

    Member admin(String nickname) throws Exception {
        Member m = member(nickname);
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", m.id());
        return m;
    }

    Res call(Member who, MockHttpServletRequestBuilder builder) throws Exception {
        MockHttpServletRequestBuilder b = AuthTestSupport.withCsrf(builder);
        if (who != null) {
            b.cookie(who.cookies());
        }
        MockHttpServletResponse res = mvc.perform(b).andReturn().getResponse();
        return new Res(res.getStatus(), res.getContentAsString(), res);
    }

    Res getAs(Member who, String url) throws Exception {
        return call(who, get(url));
    }

    Res postAs(Member who, String url, String json) throws Exception {
        return call(who, post(url).contentType(MediaType.APPLICATION_JSON).content(json == null ? "{}" : json));
    }

    Res putAs(Member who, String url, String json) throws Exception {
        return call(who, put(url).contentType(MediaType.APPLICATION_JSON).content(json == null ? "{}" : json));
    }

    Res patchAs(Member who, String url, String json) throws Exception {
        return call(who, patch(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    Res deleteAs(Member who, String url) throws Exception {
        return call(who, delete(url));
    }

    /** API로 블로그를 만들고 번호를 돌려준다. */
    long createBlog(Member owner, String address, String visibility, String joinPolicy) throws Exception {
        Res r = postAs(owner, "/api/blogs", blogJson("블로그 " + address, address, visibility, joinPolicy));
        if (r.status() != 201) {
            throw new AssertionError("블로그 생성 실패: " + r.status() + " " + r.body());
        }
        return r.json().get("id").asLong();
    }

    static String blogJson(String name, String address, String visibility, String joinPolicy) {
        return "{\"name\":\"%s\",\"address\":\"%s\",\"visibility\":\"%s\",\"joinPolicy\":\"%s\"}"
                .formatted(name, address, visibility, joinPolicy);
    }

    /** 04:00 처리를 지금 시계 시각으로 한 번 돌린다. */
    void runDaily() {
        dailyCleanupJob.runOnce();
    }

    String role(long blogId, long userId) {
        List<String> r = jdbc.queryForList("SELECT role FROM blog_members WHERE blog_id = ? AND user_id = ?",
                String.class, blogId, userId);
        return r.isEmpty() ? null : r.get(0);
    }

    final class Res {
        private final int status;
        private final String body;
        private final MockHttpServletResponse raw;

        Res(int status, String body, MockHttpServletResponse raw) {
            this.status = status;
            this.body = body;
            this.raw = raw;
        }

        int status() {
            return status;
        }

        String body() {
            return body;
        }

        MockHttpServletResponse raw() {
            return raw;
        }

        JsonNode json() throws Exception {
            return objectMapper.readTree(body);
        }

        String code() throws Exception {
            JsonNode c = json().get("code");
            return c == null ? null : c.asText();
        }

        @Override
        public String toString() {
            return status + " " + body;
        }
    }
}
