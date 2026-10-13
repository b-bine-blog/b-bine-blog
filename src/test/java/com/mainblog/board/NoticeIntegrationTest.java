package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 메인 공지사항 (004 T051, US10, FR-065, D-100 #19). */
class NoticeIntegrationTest extends BoardTestBase {

    @Test
    void 관리자만_쓰고_일반_회원과_블로그장은_403() throws Exception {
        BlogFixture b = blog("notice-deny", "PUBLIC");
        String body = postJson("공지", "내용", null, List.of("무시"));
        Res user = postAs(member("회원"), "/api/notices", body);
        assertThat(user.status()).isEqualTo(403);
        assertThat(user.code()).isEqualTo("ADMIN_ONLY");
        assertThat(postAs(b.owner(), "/api/notices", body).status()).isEqualTo(403);
        Res ok = postAs(admin("관리자"), "/api/notices", body);
        assertThat(ok.status()).isEqualTo(201);
        assertThat(ok.json().get("boardType").asText()).isEqualTo("NOTICE");
        assertThat(ok.json().get("blog").isNull()).isTrue();
        assertThat(ok.json().get("tags")).isEmpty();
        assertThat(ok.json().get("canComment").asBoolean()).isFalse();
    }

    @Test
    void 다른_관리자도_고치고_지운다() throws Exception {
        Member a1 = admin("관리일");
        Member a2 = admin("관리이");
        long id = postAs(a1, "/api/notices", postJson("공지", "내용", null, null)).json().get("id").asLong();
        assertThat(putAs(a2, "/api/posts/" + id, postJson("고친 공지", "내용", null, null)).status()).isEqualTo(200);
        assertThat(putAs(member("회원"), "/api/posts/" + id, postJson("x", "y", null, null)).status()).isEqualTo(403);
        assertThat(deleteAs(a2, "/api/posts/" + id).status()).isEqualTo(204);
        assertThat(getAs(null, "/api/notices").json().get("items")).isEmpty();
    }

    @Test
    void 공지_알림은_켠_회원에게만_관리자에게는_안_간다() throws Exception {
        Member on = member("켬");
        Member off = member("끔");
        setNotification(off.id(), "notice", false);
        Member adm = admin("관리자");
        long id = postAs(adm, "/api/notices", postJson("새 공지", "내용", null, null)).json().get("id").asLong();
        assertThat(notificationsOf(on.id())).containsExactly("NOTICE");
        assertThat(notificationsOf(off.id())).isEmpty();
        assertThat(notificationsOf(adm.id())).isEmpty();
        JsonNode list = getAs(null, "/api/notices").json();
        assertThat(list.get("items").get(0).get("id").asLong()).isEqualTo(id);
        assertThat(getAs(null, "/api/posts/" + id).json().get("title").asText()).isEqualTo("새 공지");
    }

    @Test
    void 공지_이미지는_관리자만() throws Exception {
        assertThat(upload(member("회원"), "/api/notices/images", png("a.png")).code()).isEqualTo("ADMIN_ONLY");
        assertThat(upload(admin("관리자"), "/api/notices/images", png("a.png")).status()).isEqualTo(201);
    }
}
