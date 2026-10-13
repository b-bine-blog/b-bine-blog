package com.mainblog.blog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** US6 블로그 탈퇴 (T054, BLG-07, FR-064, FR-065, D-33). */
class LeaveBlogIntegrationTest extends BlogTestBase {

    @Test
    void 멤버가_나가면_글은_탈퇴한_계정으로_표시되고_멤버_수가_준다() throws Exception {
        Member owner = member("장");
        long blog = createBlog(owner, "leave-blog", "PUBLIC", "FREE");
        Member z = member("나감");
        assertThat(postAs(z, "/api/blogs/" + blog + "/join-requests", null).status()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT member_count FROM blogs WHERE id = ?", Integer.class, blog)).isEqualTo(2);
        jdbc.update("INSERT INTO posts (blog_id, author_id, title, content) VALUES (?, ?, '내 글', '본문')", blog, z.id());

        assertThat(deleteAs(z, "/api/blogs/" + blog + "/members/me").status()).isEqualTo(204);
        assertThat(role(blog, z.id())).isNull();
        assertThat(jdbc.queryForObject("SELECT author_left FROM posts WHERE author_id = ?", Boolean.class, z.id()))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT member_count FROM blogs WHERE id = ?", Integer.class, blog)).isEqualTo(1);
        assertThat(deleteAs(z, "/api/blogs/" + blog + "/members/me").status()).isEqualTo(403);
    }

    @Test
    void 블로그장은_위임_없이_나갈_수_없다() throws Exception {
        Member owner = member("장2");
        long blog = createBlog(owner, "leave-owner", "PUBLIC", "FREE");
        Res r = deleteAs(owner, "/api/blogs/" + blog + "/members/me");
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("OWNER_CANNOT_LEAVE");
        assertThat(role(blog, owner.id())).isEqualTo("OWNER");
    }

    @Test
    void 위임받을_사람이_나가면_대기_중_위임은_자동_취소된다() throws Exception {
        Member owner = member("장3");
        long blog = createBlog(owner, "leave-dele", "PUBLIC", "FREE");
        Member z = member("받을사람");
        addMember(blog, z.id(), "MEMBER");
        postAs(owner, "/api/blogs/" + blog + "/delegation", "{\"toUserId\":" + z.id() + "}");
        deleteAs(z, "/api/blogs/" + blog + "/members/me");
        assertThat(jdbc.queryForObject("SELECT status FROM blog_delegations WHERE blog_id = ?", String.class, blog))
                .isEqualTo("AUTO_CANCELED");
        assertThat(notificationsOf(owner.id())).containsExactly("DELEGATION_CANCELED");
    }
}
