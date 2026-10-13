package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 관리자의 블로그 활동 거부 (005 T014, US1-8, FR-004, SC-008, D-90). 공지 쓰기는 된다. */
class AdminBlogActivityDeniedTest extends AdminTestBase {

    @Test
    void 관리자는_블로그_만들기_참여신청_글쓰기_댓글_좋아요_팔로우가_403이다() throws Exception {
        Member admin = admin("관리자1");
        BlogFixture b = blog("act-blog", "PUBLIC");
        long postId = writePost(b.owner(), b.blogId(), "제목", "본문");

        Res create = postAs(admin, "/api/blogs", blogJson("관리자 블로그", "admin-own", "PUBLIC", "FREE"));
        assertThat(create.status()).isEqualTo(403);
        assertThat(create.code()).isEqualTo("ADMIN_NOT_ALLOWED");
        Res join = postAs(admin, "/api/blogs/" + b.blogId() + "/join-requests", "{}");
        assertThat(join.status()).isEqualTo(403);
        assertThat(join.code()).isEqualTo("ADMIN_NOT_ALLOWED");
        Res write = postAs(admin, "/api/blogs/" + b.blogId() + "/posts", postJson("t", "c", null, List.of()));
        assertThat(write.status()).isEqualTo(403);
        assertThat(write.code()).isEqualTo("ADMIN_NOT_ALLOWED");
        Res comment = postAs(admin, "/api/posts/" + postId + "/comments", "{\"content\":\"댓글\"}");
        assertThat(comment.status()).isEqualTo(403);
        assertThat(comment.code()).isEqualTo("ADMIN_NOT_ALLOWED");
        Res like = putAs(admin, "/api/posts/" + postId + "/like", null);
        assertThat(like.status()).isEqualTo(403);
        Res follow = postAs(admin, "/api/users/" + b.owner().id() + "/follow", null);
        assertThat(follow.status()).isEqualTo(403);

        assertThat(count("SELECT COUNT(*) FROM blogs WHERE address = 'admin-own'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM comments")).isZero();
        assertThat(count("SELECT COUNT(*) FROM post_likes")).isZero();
        assertThat(count("SELECT COUNT(*) FROM follows")).isZero();
    }

    @Test
    void 관리자는_공지를_쓸_수_있다() throws Exception {
        Member admin = admin("관리자2");
        Res r = postAs(admin, "/api/notices", postJson("공지", "공지 본문", null, List.of()));
        assertThat(r.status()).isEqualTo(201);
    }

    @Test
    void 관리자용_회원_정지_강제_탈퇴_멤버_제재_API가_없다() throws Exception {
        Member admin = admin("관리자3");
        Member u = member("일반회원");
        assertThat(postAs(admin, "/api/admin/members/" + u.id() + "/suspension", null).status()).isIn(404, 405);
        assertThat(deleteAs(admin, "/api/admin/members/" + u.id()).status()).isIn(404, 405);
        assertThat(postAs(admin, "/api/admin/blogs/1/members/" + u.id() + "/sanctions", null).status())
                .isIn(404, 405);
    }
}
