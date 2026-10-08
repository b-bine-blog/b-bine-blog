package com.mainblog.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 블로그 구독 보강 (003 T023, US4-4~6, FR-007~FR-011, FR-040, D-102 #1·#2·#4·#5). */
class SubscriptionIntegrationTest extends SocialTestBase {

    private String url(long blogId) {
        return "/api/blogs/" + blogId + "/subscription";
    }

    @Test
    void 공개_블로그를_구독하면_블로그장에게_알림_다시_구독해도_한_번() throws Exception {
        BlogFixture b = blog("sub-public", "PUBLIC");
        Member s = member("구독러");
        assertThat(postAs(s, url(b.blogId()), null).status()).isEqualTo(204);
        assertThat(subscribed(b.blogId(), s.id())).isTrue();
        assertThat(notificationTabsOf(b.owner().id())).containsExactly("BLOG_SUBSCRIBED:SUBSCRIBE");
        assertThat(deleteAs(s, url(b.blogId())).status()).isEqualTo(204);
        assertThat(postAs(s, url(b.blogId()), null).status()).isEqualTo(204);
        assertThat(notificationsOf(b.owner().id())).containsExactly("BLOG_SUBSCRIBED");
        assertThat(getAs(b.owner(), "/api/notifications?tab=FOLLOW").json().get("items")).hasSize(1);
    }

    @Test
    void 블로그장_400_관리자_403_비회원_401() throws Exception {
        BlogFixture b = blog("sub-deny", "PUBLIC");
        assertThat(postAs(b.owner(), url(b.blogId()), null).code()).isEqualTo("SELF_NOT_ALLOWED");
        assertThat(postAs(admin("관리"), url(b.blogId()), null).code()).isEqualTo("ADMIN_NOT_ALLOWED");
        assertThat(postAs(null, url(b.blogId()), null).status()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_subscriptions", Integer.class)).isZero();
    }

    @Test
    void 비공개_블로그는_멤버만_구독한다() throws Exception {
        BlogFixture b = blog("sub-private", "PRIVATE");
        assertThat(postAs(member("남"), url(b.blogId()), null).status()).isEqualTo(404);
        Member m = memberOf(b.blogId(), "멤버");
        assertThat(postAs(m, url(b.blogId()), null).status()).isEqualTo(204);
    }

    @Test
    void 일부_공개는_공유_링크로_들어온_회원만_구독하고_구독하면_링크_없이_들어간다() throws Exception {
        BlogFixture b = blog("sub-link", "LINK");
        Member reader = member("독자");
        assertThat(postAs(reader, url(b.blogId()), null).status()).isEqualTo(404);
        String token = getAs(b.owner(), "/api/blogs/" + b.blogId() + "/share-link").json().get("token").asText();
        Cookie cookie = postAs(null, "/api/blogs/share-access", "{\"token\":\"" + token + "\"}").raw().getCookies()[0];
        assertThat(subscribed(b.blogId(), reader.id())).isFalse(); // 링크로 들어온 것만으로는 구독되지 않는다
        assertThat(call(reader, post(url(b.blogId())).cookie(cookie)).status()).isEqualTo(204);
        assertThat(getAs(reader, "/api/blogs/address/sub-link").status()).isEqualTo(200);
        assertThat(getAs(reader, "/api/me/subscriptions").json().get("items").get(0).get("address").asText())
                .isEqualTo("sub-link");
    }

    @Test
    void 일분에_31번째는_429_구독과_취소를_합쳐_센다() throws Exception {
        BlogFixture b = blog("sub-limit", "PUBLIC");
        Member s = member("연타");
        for (int i = 0; i < 15; i++) {
            assertThat(postAs(s, url(b.blogId()), null).status()).isEqualTo(204);
            assertThat(deleteAs(s, url(b.blogId())).status()).isEqualTo(204);
        }
        Res r = postAs(s, url(b.blogId()), null);
        assertThat(r.status()).isEqualTo(429);
        assertThat(r.code()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void 블로그가_폐쇄되면_구독이_지워지고_따로_알리지_않는다() throws Exception {
        clock.setTo(LocalDateTime.of(2026, 10, 2, 15, 0));
        BlogFixture b = blog("sub-close", "PUBLIC");
        Member s = member("구독자");
        postAs(s, url(b.blogId()), null);
        postAs(b.owner(), "/api/blogs/" + b.blogId() + "/closing", null);
        assertThat(subscribed(b.blogId(), s.id())).isTrue(); // 폐쇄 예정 7일 동안은 그대로
        clock.setTo(LocalDateTime.of(2026, 10, 9, 4, 0));
        runDaily();
        assertThat(jdbc.queryForObject("SELECT status FROM blogs WHERE id = ?", String.class, b.blogId()))
                .isEqualTo("CLOSED");
        assertThat(subscribed(b.blogId(), s.id())).isFalse();
        assertThat(getAs(relogin(s), "/api/me/subscriptions").json().get("items")).isEmpty();
        assertThat(notificationsOf(s.id())).containsOnly("BLOG_CLOSING");
    }

    @Test
    void 블로그장이_차단한_회원의_구독은_알림이_없다() throws Exception {
        BlogFixture b = blog("sub-block", "PUBLIC");
        Member s = member("차단됨");
        block(b.owner().id(), s.id());
        assertThat(postAs(s, url(b.blogId()), null).status()).isEqualTo(204);
        assertThat(notificationsOf(b.owner().id())).isEmpty();
    }

    @Test
    void 블로그_구독_알림을_끄면_만들지_않는다() throws Exception {
        BlogFixture b = blog("sub-mute", "PUBLIC");
        setNotification(b.owner().id(), "blog_subscribed", false);
        postAs(member("구독러2"), url(b.blogId()), null);
        assertThat(notificationsOf(b.owner().id())).isEmpty();
    }
}
