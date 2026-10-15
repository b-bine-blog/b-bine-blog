package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 메인 피드 (004 T029, US3, FR-061~FR-064, SC-008, D-89, D-100 #18). */
class FeedIntegrationTest extends BoardTestBase {

    private static List<String> titles(JsonNode items) {
        List<String> t = new ArrayList<>();
        items.forEach(i -> t.add(i.get("title").asText()));
        return t;
    }

    @Test
    void 비회원_최신_탭은_공개_블로그_글만() throws Exception {
        BlogFixture pub = blog("feed-pub", "PUBLIC");
        BlogFixture link = blog("feed-link", "LINK");
        BlogFixture priv = blog("feed-priv", "PRIVATE");
        insertPost(pub.blogId(), pub.owner().id(), "공개", "c", clock.now());
        insertPost(link.blogId(), link.owner().id(), "일부", "c", clock.now());
        insertPost(priv.blogId(), priv.owner().id(), "비공개", "c", clock.now());
        JsonNode r = getAs(null, "/api/feed").json();
        assertThat(titles(r.get("items"))).containsExactly("공개");
        assertThat(r.get("tab").asText()).isEqualTo("LATEST");
    }

    @Test
    void 최신_탭_1페이지에만_최근_공지를_고정한다() throws Exception {
        BlogFixture b = blog("feed-notice", "PUBLIC");
        for (int i = 1; i <= 11; i++) {
            insertPost(b.blogId(), b.owner().id(), "블로그 글" + i, "c", clock.now().minusHours(i));
        }
        Member adm = admin("관리자");
        for (int i = 1; i <= 4; i++) {
            postAs(adm, "/api/notices", postJson("공지" + i, "내용", null, null));
        }
        JsonNode first = getAs(null, "/api/feed?size=10").json();
        // 기본 3개, 최근 순(D-109). 공지는 일반 글 목록(items)에는 섞이지 않는다
        assertThat(titles(first.get("notices"))).containsExactly("공지4", "공지3", "공지2");
        assertThat(titles(first.get("items"))).hasSize(10).allMatch(t -> t.startsWith("블로그 글"));
        JsonNode second = getAs(null, "/api/feed?page=2&size=10").json();
        assertThat(second.get("page").asInt()).isEqualTo(2);
        assertThat(second.get("notices")).isEmpty();
        assertThat(getAs(null, "/api/feed?tab=POPULAR").json().get("notices")).isEmpty();
    }

    @Test
    void 인기_탭은_좋아요_많은_순_같으면_최신() throws Exception {
        BlogFixture b = blog("feed-pop", "PUBLIC");
        long p = insertPost(b.blogId(), b.owner().id(), "P", "c", clock.now().minusHours(3));
        long q = insertPost(b.blogId(), b.owner().id(), "Q", "c", clock.now().minusHours(2));
        long r = insertPost(b.blogId(), b.owner().id(), "R", "c", clock.now().minusHours(1));
        setLikes(p, 8);
        setLikes(q, 3);
        setLikes(r, 3);
        assertThat(titles(getAs(null, "/api/feed?tab=POPULAR").json().get("items"))).containsExactly("P", "R", "Q");
    }

    @Test
    void 구독_탭은_20개씩_이어_받고_30일_넘은_글은_빠진다() throws Exception {
        BlogFixture b = blog("feed-sub", "PUBLIC");
        Member reader = member("구독자");
        subscribe(b.blogId(), reader.id());
        for (int i = 1; i <= 25; i++) {
            insertPost(b.blogId(), b.owner().id(), "글" + i, "c", clock.now().minusHours(i));
        }
        insertPost(b.blogId(), b.owner().id(), "오래됨", "c", clock.now().minusDays(31));
        JsonNode first = getAs(reader, "/api/feed?tab=SUBSCRIBED").json();
        assertThat(first.get("items")).hasSize(20);
        assertThat(first.get("items").get(0).get("title").asText()).isEqualTo("글1");
        String cursor = first.get("nextCursor").asText();
        JsonNode second = getAs(reader, "/api/feed?tab=SUBSCRIBED&cursor="
                + URLEncoder.encode(cursor, StandardCharsets.UTF_8)).json();
        assertThat(titles(second.get("items"))).containsExactly("글21", "글22", "글23", "글24", "글25");
        assertThat(second.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void 비공개로_바뀐_블로그는_멤버_아닌_구독자에게_안_보이고_비회원은_401() throws Exception {
        BlogFixture b = blog("feed-hide", "PUBLIC");
        Member reader = member("구독자");
        subscribe(b.blogId(), reader.id());
        insertPost(b.blogId(), b.owner().id(), "글", "c", clock.now());
        assertThat(getAs(reader, "/api/feed?tab=SUBSCRIBED").json().get("items")).hasSize(1);
        jdbc.update("UPDATE blogs SET visibility = 'LINK' WHERE id = ?", b.blogId());
        assertThat(getAs(reader, "/api/feed?tab=SUBSCRIBED").json().get("items")).hasSize(1);
        jdbc.update("UPDATE blogs SET visibility = 'PRIVATE' WHERE id = ?", b.blogId());
        assertThat(getAs(reader, "/api/feed?tab=SUBSCRIBED").json().get("items")).isEmpty();
        assertThat(getAs(b.owner(), "/api/feed?tab=SUBSCRIBED").json().get("items")).isEmpty();
        Res guest = getAs(null, "/api/feed?tab=SUBSCRIBED");
        assertThat(guest.status()).isEqualTo(401);
        assertThat(getAs(null, "/api/feed?tab=FOLLOWING").status()).isEqualTo(401);
    }

    @Test
    void 삭제와_차단은_다음_조회에_바로_빠진다() throws Exception {
        BlogFixture b = blog("feed-block", "PUBLIC");
        Member writer = memberOf(b.blogId(), "작가");
        Member reader = member("독자");
        long p1 = writePost(writer, b.blogId(), "차단될", "c");
        long p2 = writePost(b.owner(), b.blogId(), "지울", "c");
        assertThat(getAs(reader, "/api/feed").json().get("items")).hasSize(2);
        block(reader.id(), writer.id());
        deleteAs(b.owner(), "/api/posts/" + p2);
        assertThat(getAs(reader, "/api/feed").json().get("items")).isEmpty();
        assertThat(getAs(null, "/api/feed").json().get("items")).hasSize(1);
        assertThat(p1).isPositive();
    }

    @Test
    void 팔로우_탭은_팔로우한_회원이_볼_수_있는_블로그에_쓴_최근_글() throws Exception {
        BlogFixture pub = blog("feed-fol", "PUBLIC");
        BlogFixture priv = blog("feed-fol2", "PRIVATE");
        Member reader = member("팔로워");
        follow(reader.id(), pub.owner().id());
        follow(reader.id(), priv.owner().id());
        insertPost(pub.blogId(), pub.owner().id(), "보임", "c", clock.now());
        insertPost(priv.blogId(), priv.owner().id(), "안보임", "c", clock.now());
        assertThat(titles(getAs(reader, "/api/feed?tab=FOLLOWING").json().get("items"))).containsExactly("보임");
    }

    private void like(long userId, long postId) {
        jdbc.update("INSERT INTO post_likes (user_id, post_id) VALUES (?, ?)", userId, postId);
    }

    @Test
    void 관심_탭은_좋아요한_구독_블로그_구독_블로그_팔로우_그_밖의_인기_순() throws Exception {
        BlogFixture liked = blog("int-liked", "PUBLIC");
        BlogFixture sub = blog("int-sub", "PUBLIC");
        BlogFixture other = blog("int-other", "PUBLIC");
        BlogFixture followedBlog = blog("int-follow", "PUBLIC");
        Member reader = member("관심독자");
        subscribe(liked.blogId(), reader.id());
        subscribe(sub.blogId(), reader.id());
        follow(reader.id(), followedBlog.owner().id());

        long old = insertPost(liked.blogId(), liked.owner().id(), "좋아요한 블로그 옛글", "c", clock.now().minusDays(2));
        like(reader.id(), old);
        insertPost(liked.blogId(), liked.owner().id(), "좋아요한 블로그 새글", "c", clock.now().minusHours(5));
        insertPost(sub.blogId(), sub.owner().id(), "구독 글", "c", clock.now().minusHours(1));
        insertPost(followedBlog.blogId(), followedBlog.owner().id(), "팔로우 글", "c", clock.now().minusMinutes(30));
        long quiet = insertPost(other.blogId(), other.owner().id(), "조용한 글", "c", clock.now());
        long hot = insertPost(other.blogId(), other.owner().id(), "인기 글", "c", clock.now().minusHours(3));
        setLikes(hot, 9);
        setLikes(quiet, 1);
        insertPost(other.blogId(), other.owner().id(), "오래된 글", "c", clock.now().minusDays(31));

        JsonNode r = getAs(reader, "/api/feed?tab=INTEREST&size=20").json();
        assertThat(titles(r.get("items"))).containsExactly("좋아요한 블로그 새글", "좋아요한 블로그 옛글", "구독 글", "팔로우 글",
                "인기 글", "조용한 글");
        assertThat(getAs(null, "/api/feed?tab=INTEREST").status()).isEqualTo(401);
    }
}
