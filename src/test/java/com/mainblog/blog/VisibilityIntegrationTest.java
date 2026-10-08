package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** US4 공개 범위와 공유 링크 (T041, BLG-01, FR-025~FR-032, SC-007). */
class VisibilityIntegrationTest extends BlogTestBase {

    private String shareToken(Member owner, long blog) throws Exception {
        Res r = getAs(owner, "/api/blogs/" + blog + "/share-link");
        assertThat(r.status()).isEqualTo(200);
        return r.json().get("token").asText();
    }

    /** 링크로 들어와 받은 쿠키. 유효하지 않으면 null. */
    private Cookie enter(String token) throws Exception {
        Res r = postAs(null, "/api/blogs/share-access", "{\"token\":\"" + token + "\"}");
        if (r.status() != 200) {
            return null;
        }
        return r.raw().getCookies()[0];
    }

    private int viewWith(Member who, String address, Cookie cookie) throws Exception {
        var b = get("/api/blogs/address/" + address);
        if (cookie != null) {
            b.cookie(cookie);
        }
        return call(who, b).status();
    }

    @Test
    void 일부_공개는_주소만으로_못_보고_링크로_들어오면_본다() throws Exception {
        Member owner = member("링크장");
        long blog = createBlog(owner, "link-blog", "LINK", "FREE");
        assertThat(getAs(null, "/api/blogs/address/link-blog").status()).isEqualTo(404);

        Cookie cookie = enter(shareToken(owner, blog));
        assertThat(cookie.getName()).isEqualTo("BLOG_SHARE_" + blog);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(viewWith(null, "link-blog", cookie)).isEqualTo(200);
        var b = get("/api/blogs/address/link-blog").cookie(cookie);
        assertThat(call(null, b).json().get("shareable").asBoolean()).isTrue();
        assertThat(postAs(null, "/api/blogs/share-access", "{\"token\":\"nope\"}").status()).isEqualTo(404);
    }

    @Test
    void 링크로_들어와도_자동_구독하지_않고_구독하면_링크_없이_들어온다() throws Exception {
        Member owner = member("링크장2");
        long blog = createBlog(owner, "link-sub", "LINK", "FREE");
        Member reader = member("독자");
        Cookie cookie = enter(shareToken(owner, blog));
        var b = get("/api/blogs/address/link-sub").cookie(cookie);
        assertThat(call(reader, b).json().get("subscribed").asBoolean()).isFalse();

        var sub = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/blogs/" + blog + "/subscription").cookie(cookie);
        assertThat(call(reader, sub).status()).isEqualTo(204);
        assertThat(viewWith(reader, "link-sub", null)).isEqualTo(200);
        Res mine = getAs(reader, "/api/me/subscriptions");
        assertThat(mine.json().get("items").get(0).get("address").asText()).isEqualTo("link-sub");
        assertThat(deleteAs(reader, "/api/blogs/" + blog + "/subscription").status()).isEqualTo(204);
        assertThat(viewWith(reader, "link-sub", null)).isEqualTo(404);
    }

    @Test
    void 볼_수_없는_블로그는_구독할_수_없다() throws Exception {
        long blog = createBlog(member("링크장3"), "link-nosub", "LINK", "FREE");
        assertThat(postAs(member("남"), "/api/blogs/" + blog + "/subscription", null).status()).isEqualTo(404);
        assertThat(postAs(null, "/api/blogs/" + blog + "/subscription", null).status()).isEqualTo(401);
    }

    @Test
    void 링크를_다시_만들면_이전_링크는_막힌다() throws Exception {
        Member owner = member("링크장4");
        long blog = createBlog(owner, "link-regen", "LINK", "FREE");
        String l1 = shareToken(owner, blog);
        Cookie oldCookie = enter(l1);

        Res regen = postAs(owner, "/api/blogs/" + blog + "/share-link", null);
        assertThat(regen.status()).isEqualTo(201);
        String l2 = regen.json().get("token").asText();
        assertThat(l2).isNotEqualTo(l1).hasSize(43);
        assertThat(regen.json().get("url").asText()).isEqualTo("/blog/link-regen?share=" + l2);

        assertThat(enter(l1)).isNull();
        assertThat(viewWith(null, "link-regen", oldCookie)).isEqualTo(404);
        assertThat(viewWith(null, "link-regen", enter(l2))).isEqualTo(200);

        Member member = member("멤버");
        addMember(blog, member.id(), "MEMBER");
        assertThat(postAs(member, "/api/blogs/" + blog + "/share-link", null).status()).isEqualTo(403);
    }

    @Test
    void 비공개는_멤버만_보고_공유_링크가_없다() throws Exception {
        Member owner = member("비공개장");
        long blog = createBlog(owner, "priv-blog", "PRIVATE", "FREE");
        Member m = member("멤버2");
        addMember(blog, m.id(), "MEMBER");
        assertThat(viewWith(m, "priv-blog", null)).isEqualTo(200);
        assertThat(viewWith(member("남2"), "priv-blog", null)).isEqualTo(404);
        assertThat(getAs(owner, "/api/blogs/" + blog + "/share-link").status()).isEqualTo(404);
        assertThat(getAs(owner, "/api/blogs/address/priv-blog").json().get("shareable").asBoolean()).isFalse();
    }

    @Test
    void 비공개로_바꾸면_구독은_남고_구독자_모두에게_알림이_가고_비멤버는_못_본다() throws Exception {
        Member owner = member("전환장");
        long blog = createBlog(owner, "to-private", "PUBLIC", "FREE");
        List<Member> subscribers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Member s = member("구독" + i);
            subscribe(blog, s.id());
            if (i < 2) {
                addMember(blog, s.id(), "MEMBER");
            }
            subscribers.add(s);
        }
        Res r = patchAs(owner, "/api/blogs/" + blog, "{\"visibility\":\"PRIVATE\"}");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().get("visibility").asText()).isEqualTo("PRIVATE");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_subscriptions WHERE blog_id = ?", Integer.class,
                blog)).isEqualTo(10);
        for (int i = 0; i < 10; i++) {
            Member s = subscribers.get(i);
            assertThat(notificationsOf(s.id())).containsExactly("BLOG_PRIVATE");
            assertThat(viewWith(s, "to-private", null)).isEqualTo(i < 2 ? 200 : 404);
        }
        assertThat(notificationsOf(owner.id())).isEmpty();
    }

    @Test
    void 일부_공개를_벗어나면_링크가_막히고_들어오면_새_링크가_생긴다() throws Exception {
        Member owner = member("전환장2");
        long blog = createBlog(owner, "link-switch", "LINK", "FREE");
        String l1 = shareToken(owner, blog);
        assertThat(patchAs(owner, "/api/blogs/" + blog, "{\"visibility\":\"PUBLIC\"}").status()).isEqualTo(200);
        assertThat(enter(l1)).isNull();
        assertThat(getAs(owner, "/api/blogs/" + blog + "/share-link").status()).isEqualTo(404);

        assertThat(patchAs(owner, "/api/blogs/" + blog, "{\"visibility\":\"LINK\"}").status()).isEqualTo(200);
        String l2 = shareToken(owner, blog);
        assertThat(l2).isNotEqualTo(l1);
        assertThat(enter(l1)).isNull();
        assertThat(enter(l2)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_share_links WHERE blog_id = ? AND active",
                Integer.class, blog)).isEqualTo(1);
    }

    @Test
    void 비공개를_공개로_바꿀_때도_개수_제한이_걸린다() throws Exception {
        Member owner = member("많이");
        createBlog(owner, "pub-1", "PUBLIC", "FREE");
        createBlog(owner, "pub-2", "LINK", "FREE");
        createBlog(owner, "pub-3", "PUBLIC", "FREE");
        long priv = createBlog(owner, "priv-1", "PRIVATE", "FREE");
        Res r = patchAs(owner, "/api/blogs/" + priv, "{\"visibility\":\"PUBLIC\"}");
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("BLOG_LIMIT_EXCEEDED");
        assertThat(jdbc.queryForObject("SELECT visibility FROM blogs WHERE id = ?", String.class, priv))
                .isEqualTo("PRIVATE");
        // 같은 묶음(공개↔일부 공개) 안에서는 바꿀 수 있다
        long pub1 = jdbc.queryForObject("SELECT id FROM blogs WHERE address = 'pub-1'", Long.class);
        assertThat(patchAs(owner, "/api/blogs/" + pub1, "{\"visibility\":\"LINK\"}").status()).isEqualTo(200);
    }

    @Test
    void 정보_수정_권한만_있는_부블로그장은_이름_소개만_바꾼다() throws Exception {
        Member owner = member("정보장");
        long blog = createBlog(owner, "info-perm", "PUBLIC", "FREE");
        Member manager = member("정보부장");
        addMember(blog, manager.id(), "MANAGER");
        setManagerPerms(blog, manager.id(), true, false, false);

        Res ok = patchAs(manager, "/api/blogs/" + blog,
                "{\"name\":\"새 이름\",\"description\":\"새 소개\",\"tags\":[\"#제주 여행\"]}");
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.json().get("name").asText()).isEqualTo("새 이름");
        assertThat(ok.json().get("tags").get(0).asText()).isEqualTo("제주_여행");

        assertThat(patchAs(manager, "/api/blogs/" + blog, "{\"visibility\":\"PRIVATE\"}").status()).isEqualTo(403);
        assertThat(patchAs(manager, "/api/blogs/" + blog, "{\"joinPolicy\":\"APPROVAL\"}").status()).isEqualTo(403);
        // 하나라도 권한이 없으면 아무것도 바꾸지 않는다
        assertThat(patchAs(manager, "/api/blogs/" + blog, "{\"name\":\"또\",\"joinPolicy\":\"APPROVAL\"}").status())
                .isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT name FROM blogs WHERE id = ?", String.class, blog)).isEqualTo("새 이름");

        Member member = member("그냥멤버");
        addMember(blog, member.id(), "MEMBER");
        assertThat(patchAs(member, "/api/blogs/" + blog, "{\"name\":\"x\"}").status()).isEqualTo(403);
    }

    @Test
    void 주소_알_수_없는_필드_빈_본문은_400() throws Exception {
        Member owner = member("검사장");
        long blog = createBlog(owner, "bad-patch", "PUBLIC", "FREE");
        assertThat(patchAs(owner, "/api/blogs/" + blog, "{\"address\":\"new-addr\"}").status()).isEqualTo(400);
        assertThat(patchAs(owner, "/api/blogs/" + blog, "{\"color\":\"red\"}").status()).isEqualTo(400);
        assertThat(patchAs(owner, "/api/blogs/" + blog, "{}").status()).isEqualTo(400);
        assertThat(patchAs(owner, "/api/blogs/" + blog, "{\"visibility\":\"SECRET\"}").status()).isEqualTo(400);
        assertThat(patchAs(owner, "/api/blogs/" + blog, "{\"name\":\"" + "가".repeat(31) + "\"}").status())
                .isEqualTo(400);
        Res cleared = patchAs(owner, "/api/blogs/" + blog, "{\"description\":null}");
        assertThat(cleared.status()).isEqualTo(200);
        assertThat(cleared.json().get("description").isNull()).isTrue();
    }

    @Test
    void 관리자가_숨긴_블로그는_멤버만_보고_구독자와_공유_링크와_비회원은_못_본다() throws Exception {
        // 005 T021, FR-012, D-103 #7
        Member owner = member("숨김장");
        long blog = createBlog(owner, "hide-link", "LINK", "FREE");
        Member member = member("숨김멤버");
        addMember(blog, member.id(), "MEMBER");
        Member subscriber = member("숨김구독");
        subscribe(blog, subscriber.id());
        Cookie cookie = enter(shareToken(owner, blog));
        assertThat(viewWith(null, "hide-link", cookie)).isEqualTo(200);
        assertThat(viewWith(subscriber, "hide-link", null)).isEqualTo(200);

        jdbc.update("UPDATE blogs SET hidden = TRUE, hidden_at = ? WHERE id = ?", clock.now(), blog);
        assertThat(viewWith(null, "hide-link", cookie)).isEqualTo(404);
        assertThat(viewWith(subscriber, "hide-link", null)).isEqualTo(404);
        assertThat(viewWith(member("남"), "hide-link", null)).isEqualTo(404);
        assertThat(viewWith(owner, "hide-link", null)).isEqualTo(200);
        assertThat(viewWith(member, "hide-link", null)).isEqualTo(200);
    }
}
