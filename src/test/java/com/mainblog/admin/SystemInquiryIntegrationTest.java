package com.mainblog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.mainblog.auth.application.CaptchaVerifier;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 시스템 문의 (006, ADM-08, D-112). 회원·비회원 보내기, 관리자 알림·문의함·답변, 보관 기간 정리. */
class SystemInquiryIntegrationTest extends AdminTestBase {

    @MockitoBean
    CaptchaVerifier captchaVerifier;

    @BeforeEach
    void captcha() {
        when(captchaVerifier.verify(eq("pass"), any())).thenReturn(true);
        when(captchaVerifier.verify(eq("fail"), any())).thenReturn(false);
    }

    private String body(String title, String content, String email, String captchaToken) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        m.put("content", content);
        m.put("email", email);
        m.put("captchaToken", captchaToken);
        return json(m);
    }

    private Res guestSubmit(String title, String content, String email, String captchaToken) throws Exception {
        return call(null, post("/api/inquiries").contentType(MediaType.APPLICATION_JSON)
                .content(body(title, content, email, captchaToken)));
    }

    @Test
    void 회원은_계정으로_보내고_관리자_모두에게_알림이_가며_답변은_알림과_내_문의로_본다() throws Exception {
        Member admin1 = admin("관리자1");
        Member admin2 = admin("관리자2");
        Member user = member("문의자");

        Res sent = postAs(user, "/api/inquiries", body("로그인 오류", "가끔 로그아웃돼요", "ignored@test.com", null));
        assertThat(sent.status()).isEqualTo(201);
        long id = sent.json().get("id").asLong();
        assertThat(jdbc.queryForObject("SELECT email FROM system_inquiries WHERE id = ?", String.class, id)).isNull();
        assertThat(notificationContents(admin1.id(), "INQUIRY_RECEIVED")).containsExactly("새 시스템 문의가 왔습니다: 로그인 오류");
        assertThat(notificationContents(admin2.id(), "INQUIRY_RECEIVED")).hasSize(1);
        assertThat(notificationContents(user.id(), "INQUIRY_RECEIVED")).isEmpty();

        JsonNode list = getAs(admin1, "/api/admin/inquiries").json();
        assertThat(list.get("totalElements").asLong()).isEqualTo(1);
        assertThat(list.get("items").get(0).get("sender").get("nickname").asText()).isEqualTo("문의자");

        Res answered = postAs(admin1, "/api/admin/inquiries/" + id + "/answer", json(Map.of("answer", "고쳤습니다")));
        assertThat(answered.status()).isEqualTo(200);
        assertThat(answered.json().get("status").asText()).isEqualTo("ANSWERED");
        assertThat(notificationContents(user.id(), "INQUIRY_ANSWERED"))
                .containsExactly("시스템 문의 '로그인 오류'에 답변이 달렸습니다.");
        assertThat(actionTypes()).contains("INQUIRY_ANSWER");
        assertThat(postAs(admin2, "/api/admin/inquiries/" + id + "/answer", json(Map.of("answer", "또")))
                .code()).isEqualTo("INQUIRY_ALREADY_ANSWERED");

        JsonNode mine = getAs(user, "/api/inquiries/mine").json();
        assertThat(mine.get("items").get(0).get("answer").asText()).isEqualTo("고쳤습니다");
        assertThat(getAs(admin1, "/api/admin/inquiries?status=PENDING").json().get("totalElements").asLong()).isZero();
        assertThat(getAs(admin1, "/api/admin/inquiries?status=ANSWERED").json().get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void 비회원은_이메일과_사람_확인이_있어야_하고_답변은_메일로_가며_관리자에게는_이메일이_가려진다() throws Exception {
        Member admin = admin("관리자3");

        assertThat(guestSubmit("제목", "내용", null, "pass").status()).isEqualTo(400);
        assertThat(guestSubmit("제목", "내용", "not-an-email", "pass").status()).isEqualTo(400);
        assertThat(guestSubmit("제목", "내용", "guest@test.com", null).code()).isEqualTo("CAPTCHA_REQUIRED");
        assertThat(guestSubmit("제목", "내용", "guest@test.com", "fail").code()).isEqualTo("CAPTCHA_FAILED");
        Res sent = guestSubmit("가입이 안 돼요", "인증 메일이 안 와요", "Guest@Test.com", "pass");
        assertThat(sent.status()).isEqualTo(201);
        long id = sent.json().get("id").asLong();

        JsonNode detail = getAs(admin, "/api/admin/inquiries/" + id).json();
        assertThat(detail.get("sender").isNull()).isTrue();
        assertThat(detail.get("maskedEmail").asText()).doesNotContain("guest@test.com").contains("***");
        assertThat(detail.toString()).doesNotContain("guest@test.com");

        postAs(admin, "/api/admin/inquiries/" + id + "/answer", json(Map.of("answer", "스팸함을 확인해 주세요")));
        awaitMails(1);
        MimeMessage mail = lastMailTo("guest@test.com");
        assertThat(String.valueOf(mail.getContent())).contains("가입이 안 돼요").contains("스팸함을 확인해 주세요");
    }

    @Test
    void 제목과_내용_길이를_검사하고_같은_IP는_하루_5건까지다() throws Exception {
        Member user = member("많이보냄");
        assertThat(postAs(user, "/api/inquiries", body(" ", "내용", null, null)).status()).isEqualTo(400);
        assertThat(postAs(user, "/api/inquiries", body("가".repeat(31), "내용", null, null)).status()).isEqualTo(400);
        assertThat(postAs(user, "/api/inquiries", body("제목", "가".repeat(2001), null, null)).status()).isEqualTo(400);
        for (int i = 0; i < 5; i++) {
            assertThat(postAs(user, "/api/inquiries", body("제목" + i, "내용", null, null)).status()).isEqualTo(201);
        }
        assertThat(postAs(user, "/api/inquiries", body("여섯째", "내용", null, null)).code()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void 관리자가_아니면_문의함을_볼_수_없고_비회원은_내_문의가_없다() throws Exception {
        Member user = member("일반");
        assertThat(getAs(user, "/api/admin/inquiries").status()).isEqualTo(403);
        assertThat(getAs(null, "/api/admin/inquiries").status()).isEqualTo(401);
        assertThat(getAs(null, "/api/inquiries/mine").status()).isEqualTo(401);
    }

    @Test
    void 보관_기간이_지난_문의는_04시_정리에서_지운다() throws Exception {
        Member user = member("오래된");
        long old = postAs(user, "/api/inquiries", body("옛 문의", "내용", null, null)).json().get("id").asLong();
        clock.advance(Duration.ofDays(200));
        long recent = postAs(relogin(user), "/api/inquiries", body("새 문의", "내용", null, null)).json().get("id").asLong();
        clock.advance(Duration.ofDays(170));
        runDaily();
        assertThat(count("SELECT COUNT(*) FROM system_inquiries WHERE id = ?", old)).isZero();
        assertThat(count("SELECT COUNT(*) FROM system_inquiries WHERE id = ?", recent)).isEqualTo(1);
    }
}
