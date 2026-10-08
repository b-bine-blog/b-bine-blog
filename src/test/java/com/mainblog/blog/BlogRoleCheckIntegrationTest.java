package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 블로그별 역할 확인 (T038, SEC-07, SEC-11, FR-034~FR-036, SC-004).
 * A의 블로그장이고 B의 멤버인 X가 B의 운영 API를 직접 부르면 모두 403이어야 한다.
 * 운영 API가 생기면 {@link #MANAGE_APIS}에 더한다.
 */
class BlogRoleCheckIntegrationTest extends BlogTestBase {

    /** 메서드, 주소({blog}=블로그 번호, {req}=참여 신청 번호), 본문. */
    record Api(String method, String path, String body) {}

    static final List<Api> MANAGE_APIS = List.of(
            new Api("GET", "/api/blogs/{blog}/join-requests", null),
            new Api("POST", "/api/blogs/{blog}/join-requests/{req}/approval", null),
            new Api("POST", "/api/blogs/{blog}/join-requests/{req}/rejection", null),
            new Api("DELETE", "/api/blogs/{blog}/cover-image", null));

    private Res send(Member who, Api api, long blog, long req) throws Exception {
        String url = api.path().replace("{blog}", String.valueOf(blog)).replace("{req}", String.valueOf(req));
        return switch (api.method()) {
            case "GET" -> getAs(who, url);
            case "POST" -> postAs(who, url, api.body());
            case "PUT" -> putAs(who, url, api.body());
            case "PATCH" -> patchAs(who, url, api.body() == null ? "{}" : api.body());
            case "DELETE" -> deleteAs(who, url);
            default -> throw new IllegalArgumentException(api.method());
        };
    }

    @Test
    void 다른_블로그의_블로그장이라도_멤버인_블로그의_운영_API는_403() throws Exception {
        Member x = member("엑스");
        createBlog(x, "x-own-a", "PUBLIC", "FREE");
        Member ownerB = member("비장");
        long b = createBlog(ownerB, "b-blog", "PUBLIC", "APPROVAL");
        addMember(b, x.id(), "MEMBER");
        Member applicant = member("신청자");
        long req = postAs(applicant, "/api/blogs/" + b + "/join-requests", null).json().get("id").asLong();

        for (Api api : MANAGE_APIS) {
            Res r = send(x, api, b, req);
            assertThat(r.status()).as(api.method() + " " + api.path()).isEqualTo(403);
            assertThat(r.code()).as(api.method() + " " + api.path()).isEqualTo("BLOG_FORBIDDEN");
        }
        // 확인 뒤에도 신청은 그대로 대기 중
        assertThat(jdbc.queryForObject("SELECT status FROM blog_join_requests WHERE id = ?", String.class, req))
                .isEqualTo("PENDING");
    }

    @Test
    void 비회원은_운영_API에서_401() throws Exception {
        long b = createBlog(member("비장2"), "b-blog2", "PUBLIC", "APPROVAL");
        for (Api api : MANAGE_APIS) {
            assertThat(send(null, api, b, 1).status()).as(api.path()).isEqualTo(401);
        }
    }

    @Test
    void 부블로그장_권한을_끄면_같은_쿠키로_바로_거부된다() throws Exception {
        Member owner = member("장");
        long b = createBlog(owner, "perm-off", "PUBLIC", "APPROVAL");
        Member manager = member("부장");
        addMember(b, manager.id(), "MANAGER");
        setManagerPerms(b, manager.id(), false, true, false);
        assertThat(getAs(manager, "/api/blogs/" + b + "/join-requests").status()).isEqualTo(200);

        setManagerPerms(b, manager.id(), false, false, false);
        assertThat(getAs(manager, "/api/blogs/" + b + "/join-requests").status()).isEqualTo(403);
    }

    @Test
    void 블로그에서_정지되면_같은_쿠키로_열람부터_거부된다() throws Exception {
        Member owner = member("장2");
        long b = createBlog(owner, "suspend-now", "PUBLIC", "FREE");
        Member m = member("정지");
        addMember(b, m.id(), "MEMBER");
        assertThat(getAs(m, "/api/blogs/address/suspend-now").status()).isEqualTo(200);

        jdbc.update("INSERT INTO blog_sanctions (blog_id, user_id, handled_by, sanction_type, suspend_period, reason, "
                        + "starts_at, ends_at) VALUES (?, ?, ?, 'SUSPEND', 'DAYS_3', '도배', ?, ?)",
                b, m.id(), owner.id(), clock.now(), clock.now().plusDays(3));
        Res r = getAs(m, "/api/blogs/address/suspend-now");
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.code()).isEqualTo("BLOG_SUSPENDED");
        assertThat(r.json().has("suspendedUntil")).isTrue();
    }

    @Test
    void 멤버에서_빠지면_비공개_블로그를_바로_볼_수_없다() throws Exception {
        Member owner = member("장3");
        long b = createBlog(owner, "priv-kick", "PRIVATE", "FREE");
        Member m = member("빠짐");
        addMember(b, m.id(), "MEMBER");
        assertThat(getAs(m, "/api/blogs/address/priv-kick").status()).isEqualTo(200);
        jdbc.update("DELETE FROM blog_members WHERE blog_id = ? AND user_id = ?", b, m.id());
        assertThat(getAs(m, "/api/blogs/address/priv-kick").status()).isEqualTo(404);
    }
}
