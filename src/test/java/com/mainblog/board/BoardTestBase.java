package com.mainblog.board;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.mainblog.blog.BlogTestBase;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** 004 게시판 통합 테스트 도우미 (T013). 002 BlogTestBase에 글·카테고리·차단·팔로우 데이터 만들기를 더한다. */
public abstract class BoardTestBase extends BlogTestBase {

    private static final java.util.concurrent.atomic.AtomicInteger OWNER_SEQ = new java.util.concurrent.atomic.AtomicInteger();

    /** 테스트마다 다른 IP로 불러 조회수 IP 제한(메모리)이 다른 테스트에 번지지 않게 한다. */
    protected String ip;

    @BeforeEach
    void pickIp() {
        ip = nextIp();
    }

    @Override
    protected Res call(Member who, MockHttpServletRequestBuilder builder) throws Exception {
        String addr = ip;
        return super.call(who, builder.with(r -> {
            r.setRemoteAddr(addr);
            return r;
        }));
    }

    /** 블로그장 + 블로그. 블로그장 Member를 함께 돌려준다. */
    protected record BlogFixture(long blogId, Member owner) {}

    protected BlogFixture blog(String address, String visibility) throws Exception {
        Member owner = member("주인" + OWNER_SEQ.incrementAndGet());
        return new BlogFixture(createBlog(owner, address, visibility, "FREE"), owner);
    }

    protected Member memberOf(long blogId, String nickname) throws Exception {
        Member m = member(nickname);
        addMember(blogId, m.id(), "MEMBER");
        return m;
    }

    protected String postJson(String title, String content, Long categoryId, List<String> tags) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", content);
        body.put("categoryId", categoryId);
        body.put("tags", tags);
        return objectMapper.writeValueAsString(body);
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** API로 글을 쓰고 번호를 돌려준다. */
    protected long writePost(Member who, long blogId, String title, String content) throws Exception {
        Res r = postAs(who, "/api/blogs/" + blogId + "/posts", postJson(title, content, null, List.of()));
        if (r.status() != 201) {
            throw new AssertionError("글 쓰기 실패: " + r);
        }
        return r.json().get("id").asLong();
    }

    /** 많은 글을 빠르게 넣는다(목록·피드·검색 테스트용). */
    protected long insertPost(long blogId, long authorId, String title, String content, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO posts (blog_id, author_id, board_type, title, content, status, created_at, updated_at) "
                + "VALUES (?, ?, 'BLOG', ?, ?, 'VISIBLE', ?, ?)", blogId, authorId, title, content, createdAt, createdAt);
        return jdbc.queryForObject("SELECT MAX(id) FROM posts", Long.class);
    }

    protected void setLikes(long postId, int likeCount) {
        jdbc.update("UPDATE posts SET like_count = ? WHERE id = ?", likeCount, postId);
    }

    protected long insertCategory(long blogId, String name) {
        jdbc.update("INSERT INTO categories (blog_id, name, sort_order) VALUES (?, ?, 1)", blogId, name);
        return jdbc.queryForObject("SELECT MAX(id) FROM categories", Long.class);
    }

    protected void block(long blockerId, long blockedId) {
        jdbc.update("INSERT INTO user_blocks (blocker_id, blocked_id) VALUES (?, ?)", blockerId, blockedId);
    }

    protected void follow(long followerId, long followeeId) {
        jdbc.update("INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)", followerId, followeeId);
    }

    protected void setNotification(long userId, String column, boolean on) {
        jdbc.update("INSERT INTO notification_settings (user_id, " + column + ") VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE " + column + " = VALUES(" + column + ")", userId, on);
    }

    protected Res upload(Member who, String url, MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder b = multipart(url);
        for (MockMultipartFile f : files) {
            b.file(f);
        }
        return call(who, b);
    }

    protected static MockMultipartFile png(String name) throws Exception {
        return new MockMultipartFile("files", name, "image/png", image("png", 16));
    }

    protected static byte[] image(String format, int size) throws Exception {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        img.setRGB(1, 1, Color.BLUE.getRGB());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** 올린 이미지의 /files/ 주소. */
    protected String uploadOne(Member who, long blogId) throws Exception {
        Res r = upload(who, "/api/blogs/" + blogId + "/images", png("a.png"));
        if (r.status() != 201) {
            throw new AssertionError("이미지 올리기 실패: " + r);
        }
        return r.json().get("images").get(0).get("url").asText();
    }
}
