package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 002 보안 점검 (T075, 헌장 I·II·III, SEC-07, SEC-11, 4.4, 4.6, D-22).
 * - 블로그 API를 새로 만들면 운영 API 권한 검사 목록이나 멤버·방문자용 목록 중 하나에 들어가야 한다.
 * - 강퇴·재신청 흐름에서 오류 응답과 로그에 이메일·전화번호·해시가 남지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class BlogSecurityTest extends BlogTestBase {

    /** 블로그 운영 권한 없이 부를 수 있는 API. 각자 서비스에서 대상 본인·열람 가능 여부를 확인한다. */
    static final Set<String> MEMBER_FACING = Set.of(
            "POST /api/blogs/{blog}/join-requests",
            "POST /api/blogs/{blog}/subscription",
            "DELETE /api/blogs/{blog}/subscription",
            "GET /api/blogs/{blog}/share-link",
            "DELETE /api/blogs/{blog}/members/me",
            "GET /api/blogs/{blog}/delegation",
            "POST /api/blogs/{blog}/delegation/acceptance",
            "POST /api/blogs/{blog}/delegation/rejection",
            "POST /api/blogs/{blog}/blacklist-appeals");
    /** 004 게시판 API. 쓰기·카테고리 권한은 board 패키지의 통합 테스트(PostWrite·Category·PostImage)에서 확인한다. */
    static final Set<String> BOARD_APIS = Set.of(
            "GET /api/blogs/{blog}/posts",
            "POST /api/blogs/{blog}/posts",
            "POST /api/blogs/{blog}/images",
            "GET /api/blogs/{blog}/categories",
            "POST /api/blogs/{blog}/categories",
            "PUT /api/blogs/{blog}/categories/order",
            "PATCH /api/blogs/{blog}/categories/{categoryId}",
            "DELETE /api/blogs/{blog}/categories/{categoryId}");
    /** multipart라 BlogRoleCheckIntegrationTest 목록 대신 아래에서 따로 확인한다. */
    static final String COVER_UPLOAD = "PUT /api/blogs/{blog}/cover-image";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    /** /api/blogs/{blogId}로 시작하는 매핑. 경로 변수 이름은 {blog}, {req}로 맞춘다. */
    private Set<String> blogScopedOperations() {
        Set<String> ops = new TreeSet<>();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> info.getPatternValues().stream()
                .filter(p -> p.startsWith("/api/blogs/{blogId}"))
                .forEach(p -> info.getMethodsCondition().getMethods().forEach(m -> ops.add(m.name() + " "
                        + p.replace("{blogId}", "{blog}").replaceAll("\\{(requestId|userId|reportId|appealId)}", "{req}")))));
        return ops;
    }

    @Test
    void 블로그별_API는_모두_권한_검사_목록이나_멤버용_목록에_있다() {
        Set<String> checked = BlogRoleCheckIntegrationTest.MANAGE_APIS.stream()
                .map(a -> a.method() + " " + a.path()).collect(Collectors.toSet());
        Set<String> unknown = new TreeSet<>(blogScopedOperations());
        unknown.removeAll(checked);
        unknown.removeAll(MEMBER_FACING);
        unknown.remove(COVER_UPLOAD);
        unknown.removeAll(BOARD_APIS);
        assertThat(unknown).as("새 블로그 API는 BlogRoleCheckIntegrationTest.MANAGE_APIS나 MEMBER_FACING에 더한다")
                .isEmpty();
    }

    @Test
    void 대표_이미지_올리기는_정보_수정_권한이_없으면_403() throws Exception {
        Member owner = member("장");
        long b = createBlog(owner, "sec-cover", "PUBLIC", "FREE");
        Member m = member("멤버");
        addMember(b, m.id(), "MEMBER");
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1, 2, 3});

        Res r = call(m, multipart(org.springframework.http.HttpMethod.PUT, "/api/blogs/" + b + "/cover-image").file(file));
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.code()).isEqualTo("BLOG_FORBIDDEN");
        assertThat(call(null, multipart(org.springframework.http.HttpMethod.PUT, "/api/blogs/" + b + "/cover-image")
                .file(file)).status()).isEqualTo(401);
    }

    @Test
    void 강퇴와_재신청_흐름의_응답과_로그에_개인정보와_해시가_없다(CapturedOutput output) throws Exception {
        Member owner = member("장2");
        long b = createBlog(owner, "sec-pii", "PUBLIC", "FREE");
        long target = createUser("pii-target@test.com", PW, "대상", "박보안", "01055554444");
        Member m = new Member(target, "pii-target@test.com", loginAs("pii-target@test.com", PW, false),
                new java.util.ArrayList<>());
        addMember(b, target, "MEMBER");

        StringBuilder bodies = new StringBuilder();
        bodies.append(getAs(owner, "/api/blogs/" + b + "/members").body());
        bodies.append(postAs(owner, "/api/blogs/" + b + "/members/" + target + "/sanctions",
                "{\"type\":\"KICK\",\"reason\":\"광고\"}").body());
        bodies.append(getAs(owner, "/api/blogs/" + b + "/blacklist").body());
        Res again = postAs(m, "/api/blogs/" + b + "/join-requests", null);
        assertThat(again.code()).isEqualTo("BLOG_BLACKLISTED");
        bodies.append(again.body());
        Res appeal = postAs(m, "/api/blogs/" + b + "/blacklist-appeals", "{\"content\":\"저 아닙니다\"}");
        bodies.append(appeal.body());
        bodies.append(getAs(owner, "/api/blogs/" + b + "/blacklist-appeals").body());
        // 없는 대상·잘못된 요청의 오류 응답도 본다
        bodies.append(postAs(owner, "/api/blogs/" + b + "/members/" + target + "/sanctions", "{\"type\":\"KICK\"}").body());
        bodies.append(postAs(owner, "/api/blogs/" + b + "/delegation", "{\"toUserId\":" + target + "}").body());

        var hashes = jdbc.queryForMap("SELECT name_hash, email_hash, phone_hash FROM blog_blacklists WHERE blog_id = ?", b);
        Set<String> secrets = new TreeSet<>(Set.of("pii-target@test.com", "01055554444", "010-5555-4444"));
        hashes.values().forEach(v -> secrets.add((String) v));

        String all = bodies.toString();
        String logs = output.getAll();
        for (String secret : secrets) {
            assertThat(all).as("응답").doesNotContain(secret);
            assertThat(logs).as("로그").doesNotContain(secret);
        }
    }
}
