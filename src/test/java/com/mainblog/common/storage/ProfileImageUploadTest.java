package com.mainblog.common.storage;

import static com.mainblog.support.AuthTestSupport.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mainblog.support.IntegrationTestBase;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

/** 프로필 사진 올리기 (T058, US5 인수 시나리오 5, FR-043, 6.3, SEC-08). */
class ProfileImageUploadTest extends IntegrationTestBase {

    static final String PASSWORD = "Abcd123!";
    static final byte[] GPS_MARKER = "GPSLatitudeRef-37.5665N".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    LocalDiskFileStorage storage;

    long memberId;
    LoginCookies login;

    @BeforeEach
    void setUp() throws Exception {
        memberId = createUser("me@x.com", PASSWORD, "나닉");
        login = loginAs("me@x.com", PASSWORD, false);
    }

    private MockHttpServletResponse upload(String filename, String contentType, byte[] content) throws Exception {
        return mvc.perform(withCsrf(multipart(HttpMethod.PUT, "/api/members/me/profile-image")
                        .file(new MockMultipartFile("file", filename, contentType, content)))
                .cookie(login.cookies())).andReturn().getResponse();
    }

    private static byte[] image(String format) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        img.setRGB(3, 3, Color.RED.getRGB());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** SOI 바로 뒤에 GPS 표시가 든 APP1(Exif) 세그먼트를 끼운다. */
    private static byte[] jpegWithExifGps() throws Exception {
        byte[] jpeg = image("jpg");
        ByteArrayOutputStream exif = new ByteArrayOutputStream();
        exif.write("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        exif.write(new byte[] {'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1, (byte) 0x88, 0x25, 0, 4, 0, 0, 0, 1, 0, 0, 0, 26,
                0, 0, 0, 0});
        exif.write(GPS_MARKER);
        byte[] payload = exif.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) ((payload.length + 2) >> 8),
                (byte) ((payload.length + 2) & 0xFF)});
        out.write(payload);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** VP8X(EXIF 표시) + VP8 + EXIF 청크로 된 webp. 내용은 확인하지 않으므로 VP8 데이터는 임의 값. */
    private static byte[] webpWithExif() {
        byte[] vp8x = chunk("VP8X", new byte[] {0x08, 0, 0, 0, 15, 0, 0, 15, 0, 0});
        byte[] vp8 = chunk("VP8 ", new byte[] {1, 2, 3, 4, 5});
        byte[] exif = chunk("EXIF", GPS_MARKER);
        int size = 4 + vp8x.length + vp8.length + exif.length;
        return ByteBuffer.allocate(8 + size).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(size)
                .put("WEBP".getBytes(StandardCharsets.US_ASCII)).put(vp8x).put(vp8).put(exif).array();
    }

    private static byte[] chunk(String fourcc, byte[] data) {
        int padded = data.length + (data.length & 1);
        return ByteBuffer.allocate(8 + padded).order(ByteOrder.LITTLE_ENDIAN)
                .put(fourcc.getBytes(StandardCharsets.US_ASCII)).putInt(data.length).put(data).array();
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private byte[] storedFile() throws Exception {
        String name = jdbc.queryForObject("SELECT stored_name FROM images WHERE owner_id = ? AND deleted_at IS NULL",
                String.class, memberId);
        assertThat(name).matches("^[0-9a-f\\-]{36}\\.(jpg|png|gif|webp)$");
        return Files.readAllBytes(storage.resolve(name));
    }

    @Test
    void jpg는_EXIF_GPS가_지워진_채_UUID_이름으로_저장된다() throws Exception {
        byte[] original = jpegWithExifGps();
        assertThat(contains(original, GPS_MARKER)).isTrue();
        MockHttpServletResponse res = upload("내 사진.JPG", "image/jpeg", original);
        assertThat(res.getStatus()).isEqualTo(200);

        byte[] saved = storedFile();
        assertThat(contains(saved, GPS_MARKER)).isFalse();
        assertThat(contains(saved, "Exif".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(saved)).getWidth()).isEqualTo(16);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM images WHERE owner_id = ?", memberId);
        assertThat(row).containsEntry("original_name", "내 사진.JPG").containsEntry("owner_type", "PROFILE")
                .containsEntry("content_type", "image/jpeg");
        assertThat(((Number) row.get("uploader_id")).longValue()).isEqualTo(memberId);
        assertThat(res.getContentAsString()).contains("/files/" + row.get("stored_name"));
        // 상단 메뉴 정보에도 바로 보인다
        mvc.perform(get("/api/auth/session").cookie(login.cookies()))
                .andExpect(jsonPath("$.profileImageUrl").value("/files/" + row.get("stored_name")));
    }

    @Test
    void webp는_EXIF_청크와_VP8X_표시가_지워진다() throws Exception {
        assertThat(upload("p.webp", "image/webp", webpWithExif()).getStatus()).isEqualTo(200);
        byte[] saved = storedFile();
        assertThat(contains(saved, GPS_MARKER)).isFalse();
        assertThat(contains(saved, "EXIF".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(contains(saved, "VP8 ".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(saved[20] & 0x08).isZero();
        int riffSize = ByteBuffer.wrap(saved, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        assertThat(riffSize).isEqualTo(saved.length - 8);
    }

    @Test
    void png_gif도_받는다() throws Exception {
        assertThat(upload("a.png", "image/png", image("png")).getStatus()).isEqualTo(200);
        assertThat(upload("a.gif", "image/gif", image("gif")).getStatus()).isEqualTo(200);
        assertThat(upload("a.jpeg", "image/jpeg", image("jpg")).getStatus()).isEqualTo(200);
        // 바꿀 때마다 이전 사진은 deleted_at, 지금 사진은 1장
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM images WHERE owner_id = ? AND deleted_at IS NULL",
                Integer.class, memberId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM images WHERE owner_id = ?", Integer.class, memberId))
                .isEqualTo(3);
    }

    @Test
    void 크기가_3MB를_넘으면_FILE_TOO_LARGE() throws Exception {
        byte[] big = new byte[3 * 1024 * 1024 + 1];
        byte[] jpg = image("jpg");
        System.arraycopy(jpg, 0, big, 0, jpg.length);
        MockHttpServletResponse res = upload("big.jpg", "image/jpeg", big);
        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(res.getContentAsString()).contains("FILE_TOO_LARGE");
    }

    @Test
    void svg와_확장자만_jpg인_png와_MIME이_다른_파일은_FILE_TYPE_NOT_ALLOWED() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        assertThat(upload("x.svg", "image/svg+xml", svg).getContentAsString()).contains("FILE_TYPE_NOT_ALLOWED");
        assertThat(upload("x.jpg", "image/jpeg", image("png")).getContentAsString())
                .contains("FILE_TYPE_NOT_ALLOWED");
        assertThat(upload("x.png", "image/jpeg", image("png")).getContentAsString())
                .contains("FILE_TYPE_NOT_ALLOWED");
        // 시그니처만 맞고 내용이 깨진 파일
        byte[] broken = new byte[64];
        System.arraycopy(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 0, broken, 0, 3);
        assertThat(upload("x.jpg", "image/jpeg", broken).getContentAsString()).contains("FILE_TYPE_NOT_ALLOWED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM images", Integer.class)).isZero();
    }

    @Test
    void 사진을_지우면_deleted_at이_찍히고_세션의_사진이_없어진다() throws Exception {
        upload("a.png", "image/png", image("png"));
        mvc.perform(withCsrf(delete("/api/members/me/profile-image")).cookie(login.cookies()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM images WHERE deleted_at IS NULL", Integer.class))
                .isZero();
        mvc.perform(get("/api/auth/session").cookie(login.cookies()))
                .andExpect(jsonPath("$.profileImageUrl").doesNotExist());
    }
}
