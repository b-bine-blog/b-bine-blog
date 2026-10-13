package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.blog.application.BlogPermission;
import com.mainblog.blog.application.BlogPermissionService;
import com.mainblog.blog.domain.BlogMember;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 권한표(data-model.md)와 BlogPermissionService.allows가 같은지 확인한다 (T039, SEC-07, FR-034~FR-036). */
class BlogPermissionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 10, 0);

    /** 역할(블로그장·부블로그장 8가지 권한 조합·멤버·비멤버) × 권한 6개. */
    static Stream<Arguments> table() {
        List<Arguments> rows = new ArrayList<>();
        for (BlogPermission p : BlogPermission.values()) {
            rows.add(Arguments.of("OWNER", BlogMember.owner(1L, 1L, NOW), p, true));
            rows.add(Arguments.of("MEMBER", BlogMember.member(1L, 2L, NOW), p,
                    p == BlogPermission.VIEW || p == BlogPermission.MEMBER));
            rows.add(Arguments.of("NONE", null, p, p == BlogPermission.VIEW));
            for (int bits = 0; bits < 8; bits++) {
                boolean info = (bits & 1) != 0;
                boolean manage = (bits & 2) != 0;
                boolean post = (bits & 4) != 0;
                BlogMember m = BlogMember.member(1L, 3L, NOW);
                m.makeManager(info, manage, post, NOW);
                boolean expected = switch (p) {
                    case VIEW, MEMBER -> true;
                    case BLOG_INFO -> info;
                    case MEMBER_MANAGE -> manage;
                    case POST_MANAGE -> post;
                    case OWNER -> false;
                };
                rows.add(Arguments.of("MANAGER(" + info + "," + manage + "," + post + ")", m, p, expected));
            }
        }
        return rows.stream();
    }

    @ParameterizedTest(name = "{0} {2} → {3}")
    @MethodSource("table")
    void 권한표와_같다(String label, BlogMember member, BlogPermission permission, boolean expected) {
        assertThat(BlogPermissionService.allows(member, permission)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    void 화면_메뉴_권한에는_VIEW_MEMBER를_넣지_않는다(String label, BlogMember member, BlogPermission p, boolean e) {
        Set<BlogPermission> menu = BlogPermissionService.permissionsOf(member);
        assertThat(menu).doesNotContain(BlogPermission.VIEW, BlogPermission.MEMBER);
        for (BlogPermission q : EnumSet.complementOf(EnumSet.of(BlogPermission.VIEW, BlogPermission.MEMBER))) {
            assertThat(menu.contains(q)).isEqualTo(BlogPermissionService.allows(member, q));
        }
    }
}
