package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 004 보안 점검 (T056, SC-002, SC-011, SEC-07, SEC-10, 4.4, D-22, D-64).
 * - 글·댓글·검색·피드·태그·공지 응답과 로그에 이메일·전화번호·실명이 없다.
 * - CSRF 토큰 없는 쓰기 요청은 403이다.
 * - 공격 문자열은 JSON 문자열로만 돌아오고, 본문 HTML에는 스크립트가 남지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class BoardSecurityTest extends BoardTestBase {

    static final List<String> ATTACKS = List.of(
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "\"><svg onload=alert(1)>",
            "javascript:alert(1)",
            "' OR '1'='1",
            "<iframe src=//evil.example>");

    private String q(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Test
    void 게시판_응답과_로그에_이메일_전화번호_실명이_없다(CapturedOutput output) throws Exception {
        BlogFixture b = blog("sec-board", "PUBLIC");
        long writerId = createUser("board-secret@test.com", PW, "비밀작가", "홍비밀", "01077778888");
        Member writer = new Member(writerId, "board-secret@test.com", loginAs("board-secret@test.com", PW, false),
                new ArrayList<>());
        addMember(b.blogId(), writerId, "MEMBER");
        Member reader = member("독자");
        follow(reader.id(), writerId);
        subscribe(b.blogId(), reader.id());
        Member admin = admin("운영자");

        Res created = postAs(writer, "/api/blogs/" + b.blogId() + "/posts",
                postJson("여행 기록", "본문", null, List.of("여행")));
        assertThat(created.status()).isEqualTo(201);
        long post = created.json().get("id").asLong();
        Res comment = postAs(writer, "/api/posts/" + post + "/comments", "{\"content\":\"댓글\"}");
        long commentId = comment.json().get("id").asLong();
        postAs(reader, "/api/posts/" + post + "/comments", "{\"content\":\"답글\",\"parentId\":" + commentId + "}");
        putAs(reader, "/api/posts/" + post + "/like", null);
        postAs(admin, "/api/notices", postJson("공지", "공지 본문", null, List.of()));

        StringBuilder bodies = new StringBuilder();
        bodies.append(created.body()).append(comment.body());
        for (String url : List.of(
                "/api/blogs/" + b.blogId() + "/posts",
                "/api/posts/" + post,
                "/api/posts/" + post + "/comments",
                "/api/search?q=" + q("여행"),
                "/api/search?q=" + q("비밀작가") + "&type=NICKNAME",
                "/api/feed?tab=LATEST",
                "/api/feed?tab=SUBSCRIBED",
                "/api/feed?tab=FOLLOWING",
                "/api/tags/" + q("여행") + "/posts",
                "/api/notices",
                "/api/me/recent-searches")) {
            Res r = getAs(reader, url);
            assertThat(r.status()).as(url).isEqualTo(200);
            bodies.append(r.body());
        }
        bodies.append(getAs(writer, "/api/posts/" + post).body());
        // 오류 응답도 본다
        bodies.append(putAs(reader, "/api/posts/" + post, postJson("남의 글", "x", null, List.of())).body());
        bodies.append(deleteAs(reader, "/api/comments/" + commentId).body());

        String all = bodies.toString();
        assertThat(all).contains("비밀작가");
        for (String secret : List.of("board-secret@test.com", "01077778888", "010-7777-8888", "홍비밀")) {
            assertThat(all).as("응답").doesNotContain(secret);
            assertThat(output.getAll()).as("로그").doesNotContain(secret);
        }
    }

    @Test
    void CSRF_토큰_없는_쓰기는_403() throws Exception {
        BlogFixture b = blog("sec-csrf", "PUBLIC");
        long post = writePost(b.owner(), b.blogId(), "글", "본문");
        List<MockHttpServletRequestBuilder> writes = List.of(
                post("/api/blogs/" + b.blogId() + "/posts").contentType(MediaType.APPLICATION_JSON)
                        .content(postJson("t", "c", null, List.of())),
                put("/api/posts/" + post).contentType(MediaType.APPLICATION_JSON)
                        .content(postJson("t", "c", null, List.of())),
                delete("/api/posts/" + post),
                post("/api/posts/" + post + "/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"c\"}"),
                put("/api/posts/" + post + "/like"),
                post("/api/blogs/" + b.blogId() + "/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"여행\"}"),
                delete("/api/me/recent-searches"));
        for (MockHttpServletRequestBuilder w : writes) {
            MockHttpServletResponse res = mvc.perform(w.cookie(b.owner().cookies())).andReturn().getResponse();
            assertThat(res.getStatus()).isEqualTo(403);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE blog_id = ? AND status = 'VISIBLE'",
                Integer.class, b.blogId())).isEqualTo(1);
    }

    @Test
    void 공격_문자열은_JSON_문자열로만_돌아오고_본문_HTML에_스크립트가_없다() throws Exception {
        BlogFixture b = blog("sec-xss", "PUBLIC");
        Member reader = member("방문자");
        for (String attack : ATTACKS) {
            String title = attack.length() > 30 ? attack.substring(0, 30) : attack;
            Res created = postAs(b.owner(), "/api/blogs/" + b.blogId() + "/posts",
                    postJson(title, attack + "\n\n[링크](javascript:alert(1))", null, List.of(attack)));
            // 태그로 쓸 수 없는 문자는 400으로 막히거나 정리된다. 어느 쪽이든 HTML이 그대로 나가면 안 된다.
            if (created.status() == 400) {
                created = postAs(b.owner(), "/api/blogs/" + b.blogId() + "/posts",
                        postJson(title, attack, null, List.of()));
            }
            assertThat(created.status()).as(attack).isEqualTo(201);
            long post = created.json().get("id").asLong();

            Res detail = getAs(reader, "/api/posts/" + post);
            assertThat(detail.raw().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
            assertThat(detail.raw().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(detail.json().get("title").asText()).isEqualTo(title);
            // 글자로 남은 "javascript:"는 괜찮다. 실행될 수 있는 태그·속성만 없으면 된다
            org.jsoup.nodes.Document html = org.jsoup.Jsoup.parseBodyFragment(detail.json().get("contentHtml").asText());
            assertThat(html.select("script, iframe, svg, object, embed")).isEmpty();
            for (org.jsoup.nodes.Element e : html.getAllElements()) {
                for (org.jsoup.nodes.Attribute a : e.attributes()) {
                    assertThat(a.getKey().toLowerCase()).as(attack).doesNotStartWith("on");
                    assertThat(a.getValue().toLowerCase().strip()).as(attack).doesNotStartWith("javascript:");
                }
            }
            for (var tag : detail.json().path("tags")) {
                assertThat(tag.asText()).doesNotContain("<", ">");
            }

            Res c = postAs(reader, "/api/posts/" + post + "/comments", json(java.util.Map.of("content", attack)));
            assertThat(c.status()).isEqualTo(201);
            Res comments = getAs(reader, "/api/posts/" + post + "/comments");
            assertThat(comments.raw().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
            assertThat(comments.json().get("items").get(0).get("content").asText()).isEqualTo(attack);

            Res search = getAs(reader, "/api/search?q=" + q(attack));
            assertThat(search.status()).as(attack).isIn(200, 400);
            assertThat(search.raw().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        }
    }
}
