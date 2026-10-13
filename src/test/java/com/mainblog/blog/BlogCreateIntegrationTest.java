package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** US1 블로그 만들기 (T019, FR-001~FR-006, FR-010, FR-013). */
class BlogCreateIntegrationTest extends BlogTestBase {

    @Test
    void 만들면_블로그장이_되고_주소로_들어갈_수_있다() throws Exception {
        Member m = member("만든이");
        Res r = postAs(m, "/api/blogs", blogJson("여행 기록", "travel-log", "PUBLIC", "APPROVAL"));
        assertThat(r.status()).isEqualTo(201);
        long id = r.json().get("id").asLong();
        assertThat(r.json().get("createdAt").isNull()).as("만든 응답에도 생성 일시가 있다").isFalse();
        assertThat(role(id, m.id())).isEqualTo("OWNER");

        Res view = getAs(m, "/api/blogs/address/travel-log");
        assertThat(view.status()).isEqualTo(200);
        assertThat(view.json().get("myRole").asText()).isEqualTo("OWNER");
        assertThat(view.json().get("myPermissions").toString()).contains("OWNER", "MEMBER_MANAGE");
        assertThat(view.json().get("memberCount").asInt()).isEqualTo(1);
        // 비회원도 공개 블로그를 본다
        assertThat(getAs(null, "/api/blogs/address/travel-log").json().get("myRole").isNull()).isTrue();
    }

    @Test
    void 주소_형식이_틀리면_만들지_않는다() throws Exception {
        Member m = member("형식");
        for (String bad : new String[] {"Travel_Log", "ab", "a".repeat(31), "한글주소"}) {
            Res r = postAs(m, "/api/blogs", blogJson("이름", bad, "PUBLIC", "FREE"));
            assertThat(r.status()).as(bad).isEqualTo(400);
            assertThat(r.body()).contains("address");
        }
    }

    @Test
    void 주소_중복과_예약어는_막고_이름_중복은_허용한다() throws Exception {
        Member a = member("갑");
        Member b = member("을");
        createBlog(a, "travel-log", "PUBLIC", "FREE");
        Res dup = postAs(b, "/api/blogs", blogJson("다른 이름", "travel-log", "PUBLIC", "FREE"));
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.code()).isEqualTo("BLOG_ADDRESS_TAKEN");
        for (String reserved : new String[] {"main", "admin", "api", "login", "signup", "search"}) {
            Res r = postAs(b, "/api/blogs", blogJson("이름", reserved, "PUBLIC", "FREE"));
            assertThat(r.status()).as(reserved).isEqualTo(400);
            assertThat(r.body()).contains("Reserved");
        }
        assertThat(postAs(b, "/api/blogs", blogJson("블로그 travel-log", "travel-2", "PUBLIC", "FREE")).status())
                .isEqualTo(201);
    }

    @Test
    void 공개는_일부_공개를_포함해_3개_비공개는_5개까지() throws Exception {
        Member m = member("많이");
        createBlog(m, "pub-1", "PUBLIC", "FREE");
        createBlog(m, "pub-2", "PUBLIC", "FREE");
        createBlog(m, "link-1", "LINK", "FREE");
        Res over = postAs(m, "/api/blogs", blogJson("넷째", "pub-4", "PUBLIC", "FREE"));
        assertThat(over.status()).isEqualTo(409);
        assertThat(over.code()).isEqualTo("BLOG_LIMIT_EXCEEDED");
        assertThat(postAs(m, "/api/blogs", blogJson("넷째", "link-4", "LINK", "FREE")).code())
                .isEqualTo("BLOG_LIMIT_EXCEEDED");
        for (int i = 1; i <= 5; i++) {
            createBlog(m, "priv-" + i, "PRIVATE", "FREE");
        }
        assertThat(postAs(m, "/api/blogs", blogJson("여섯째", "priv-6", "PRIVATE", "FREE")).code())
                .isEqualTo("BLOG_LIMIT_EXCEEDED");
    }

    @Test
    void 폐쇄된_블로그는_개수에서_빼고_폐쇄_예정은_넣는다() throws Exception {
        Member m = member("폐쇄");
        createOwnedBlog(m.id(), "closed-1", "CLOSED");
        createOwnedBlog(m.id(), "closing-1", "CLOSING");
        createBlog(m, "pub-a", "PUBLIC", "FREE");
        createBlog(m, "pub-b", "PUBLIC", "FREE");
        assertThat(postAs(m, "/api/blogs", blogJson("넷째", "pub-c", "PUBLIC", "FREE")).code())
                .isEqualTo("BLOG_LIMIT_EXCEEDED");
    }

    @Test
    void 비회원과_메인_관리자는_만들_수_없다() throws Exception {
        assertThat(postAs(null, "/api/blogs", blogJson("이름", "guest-blog", "PUBLIC", "FREE")).status())
                .isEqualTo(401);
        Member admin = admin("관리");
        Res r = postAs(admin, "/api/blogs", blogJson("이름", "admin-blog", "PUBLIC", "FREE"));
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.code()).isEqualTo("ADMIN_NOT_ALLOWED");
    }

    @Test
    void 태그는_10개까지_6_4_규칙으로_정리한다() throws Exception {
        Member m = member("태그");
        String eleven = "[\"a1\",\"a2\",\"a3\",\"a4\",\"a5\",\"a6\",\"a7\",\"a8\",\"a9\",\"a10\",\"a11\"]";
        Res over = postAs(m, "/api/blogs", "{\"name\":\"t\",\"address\":\"tag-over\",\"visibility\":\"PUBLIC\","
                + "\"joinPolicy\":\"FREE\",\"tags\":" + eleven + "}");
        assertThat(over.status()).isEqualTo(400);
        Res ok = postAs(m, "/api/blogs", "{\"name\":\"t\",\"address\":\"tag-ok\",\"visibility\":\"PUBLIC\","
                + "\"joinPolicy\":\"FREE\",\"tags\":[\"#제주 여행\",\"Java\",\"java\"]}");
        assertThat(ok.status()).isEqualTo(201);
        assertThat(ok.json().get("tags").toString()).isEqualTo("[\"제주_여행\",\"java\"]");
    }

    @Test
    void 이름은_1에서_30자_소개는_500자까지() throws Exception {
        Member m = member("길이");
        assertThat(postAs(m, "/api/blogs", blogJson("  ", "name-blank", "PUBLIC", "FREE")).status()).isEqualTo(400);
        assertThat(postAs(m, "/api/blogs", blogJson("가".repeat(31), "name-long", "PUBLIC", "FREE")).status())
                .isEqualTo(400);
        assertThat(postAs(m, "/api/blogs", blogJson("가".repeat(30), "name-30", "PUBLIC", "FREE")).status())
                .isEqualTo(201);
        Res longDesc = postAs(m, "/api/blogs", "{\"name\":\"n\",\"address\":\"desc-long\",\"visibility\":\"PRIVATE\","
                + "\"joinPolicy\":\"FREE\",\"description\":\"" + "가".repeat(501) + "\"}");
        assertThat(longDesc.status()).isEqualTo(400);
    }

    @Test
    void 일부_공개로_만들면_공유_링크가_생긴다() throws Exception {
        Member m = member("링크");
        long id = createBlog(m, "link-made", "LINK", "FREE");
        Integer links = jdbc.queryForObject("SELECT COUNT(*) FROM blog_share_links WHERE blog_id = ? AND active = TRUE",
                Integer.class, id);
        assertThat(links).isEqualTo(1);
    }
}
