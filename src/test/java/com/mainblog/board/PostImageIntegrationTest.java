package com.mainblog.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.mainblog.common.storage.LocalDiskFileStorage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

/** 글 이미지 (004 T032, US4, FR-029~FR-035, SC-003, research R-03). */
class PostImageIntegrationTest extends BoardTestBase {

    static final byte[] GPS_MARKER = "GPSLatitudeRef-37.5665N".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    LocalDiskFileStorage storage;

    private String imagesUrl(long blogId) {
        return "/api/blogs/" + blogId + "/images";
    }

    @Test
    void 다섯_형식이_올라가고_UUID_이름과_files_주소를_준다() throws Exception {
        BlogFixture b = blog("img-ok", "PUBLIC");
        Res r = upload(b.owner(), imagesUrl(b.blogId()),
                new MockMultipartFile("files", "../../etc/passwd.jpg", "image/jpeg", image("jpg", 16)),
                new MockMultipartFile("files", "b.jpeg", "image/jpeg", image("jpg", 16)),
                png("c.png"),
                new MockMultipartFile("files", "d.gif", "image/gif", image("gif", 16)));
        assertThat(r.status()).isEqualTo(201);
        JsonNode images = r.json().get("images");
        assertThat(images).hasSize(4);
        assertThat(images.get(0).get("url").asText()).matches("^/files/[0-9a-f-]{36}\\.jpg$");
        assertThat(jdbc.queryForObject("SELECT original_name FROM images WHERE id = ?", String.class,
                images.get(0).get("id").asLong())).isEqualTo("passwd.jpg");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM images WHERE owner_type = 'POST' AND owner_id IS NULL",
                Integer.class)).isEqualTo(4);
    }

    @Test
    void 장당_3MB_넘으면_400_FILE_TOO_LARGE() throws Exception {
        BlogFixture b = blog("img-big", "PUBLIC");
        byte[] big = new byte[3 * 1024 * 1024 + 1];
        byte[] head = image("png", 16);
        System.arraycopy(head, 0, big, 0, head.length);
        Res r = upload(b.owner(), imagesUrl(b.blogId()), new MockMultipartFile("files", "big.png", "image/png", big));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.code()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void 이미지가_아닌_파일과_11장은_400() throws Exception {
        BlogFixture b = blog("img-type", "PUBLIC");
        String url = imagesUrl(b.blogId());
        assertThat(upload(b.owner(), url, new MockMultipartFile("files", "a.svg", "image/svg+xml",
                "<svg onload=alert(1)>".getBytes())).code()).isEqualTo("FILE_TYPE_NOT_ALLOWED");
        assertThat(upload(b.owner(), url, new MockMultipartFile("files", "a.pdf", "application/pdf",
                "%PDF-1.4".getBytes())).code()).isEqualTo("FILE_TYPE_NOT_ALLOWED");
        assertThat(upload(b.owner(), url, new MockMultipartFile("files", "evil.png", "image/png",
                new byte[] {'M', 'Z', 0, 0, 1, 2, 3})).code()).isEqualTo("FILE_TYPE_NOT_ALLOWED");
        List<MockMultipartFile> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add(png("p" + i + ".png"));
        }
        Res r = upload(b.owner(), url, eleven.toArray(MockMultipartFile[]::new));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.code()).isEqualTo("POST_IMAGE_LIMIT");
    }

    @Test
    void 저장_파일에는_GPS_EXIF가_없다() throws Exception {
        BlogFixture b = blog("img-exif", "PUBLIC");
        byte[] jpeg = image("jpg", 16);
        ByteArrayOutputStream exif = new ByteArrayOutputStream();
        exif.write("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        exif.write(GPS_MARKER);
        byte[] payload = exif.toByteArray();
        ByteArrayOutputStream withExif = new ByteArrayOutputStream();
        withExif.write(jpeg, 0, 2);
        withExif.write(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) ((payload.length + 2) >> 8),
                (byte) ((payload.length + 2) & 0xFF)});
        withExif.write(payload);
        withExif.write(jpeg, 2, jpeg.length - 2);
        Res r = upload(b.owner(), imagesUrl(b.blogId()),
                new MockMultipartFile("files", "gps.jpg", "image/jpeg", withExif.toByteArray()));
        assertThat(r.status()).isEqualTo(201);
        String url = r.json().get("images").get(0).get("url").asText();
        byte[] saved = Files.readAllBytes(storage.root().resolve(url.substring("/files/".length())));
        assertThat(new String(saved, StandardCharsets.ISO_8859_1)).doesNotContain("GPSLatitudeRef", "Exif");
    }

    @Test
    void 멤버가_아니거나_관리자면_403() throws Exception {
        BlogFixture b = blog("img-deny", "PUBLIC");
        assertThat(upload(member("밖"), imagesUrl(b.blogId()), png("a.png")).status()).isEqualTo(403);
        assertThat(upload(admin("관리"), imagesUrl(b.blogId()), png("a.png")).code()).isEqualTo("ADMIN_NOT_ALLOWED");
    }

    @Test
    void 글에는_본인이_올린_이미지만_잇고_수정에서_빠지면_소속을_비운다() throws Exception {
        BlogFixture b = blog("img-link", "PUBLIC");
        Member other = memberOf(b.blogId(), "남");
        String mine = uploadOne(b.owner(), b.blogId());
        String mine2 = uploadOne(b.owner(), b.blogId());
        String theirs = uploadOne(other, b.blogId());
        long id = writePost(b.owner(), b.blogId(), "t", "![a](" + mine + ") ![b](" + mine2 + ") ![c](" + theirs + ")");
        assertThat(jdbc.queryForList("SELECT owner_id FROM images WHERE owner_id IS NOT NULL", Long.class))
                .containsOnly(id).hasSize(2);
        assertThat(putAs(b.owner(), "/api/posts/" + id, postJson("t", "![a](" + mine + ")", null, null)).status())
                .isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM images WHERE owner_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT owner_id FROM images WHERE stored_name = ?", Long.class,
                mine2.substring(7))).isNull();
    }

    @Test
    void 서로_다른_이미지가_10장을_넘는_글은_400() throws Exception {
        BlogFixture b = blog("img-limit", "PUBLIC");
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            body.append("![x](").append(uploadOne(b.owner(), b.blogId())).append(")\n");
        }
        Res r = postAs(b.owner(), "/api/blogs/" + b.blogId() + "/posts", postJson("t", body.toString(), null, null));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.code()).isEqualTo("POST_IMAGE_LIMIT");
    }
}
