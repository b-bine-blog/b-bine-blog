package com.mainblog.contract;

import static com.mainblog.support.AuthTestSupport.postJson;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mainblog.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

/** 가입 API 계약 (T026). contracts/member-auth-api.yaml의 signup 태그와 상태 코드·본문 필드가 같은지. */
class SignupContractTest extends IntegrationTestBase {

    @Test
    void 인증번호_발송은_202_본문_없음() throws Exception {
        mvc.perform(postJson("/api/auth/signup/email-code", "{\"email\":\"c@x.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
    }

    @Test
    void 잘못된_이메일은_400_VALIDATION_FAILED와_fieldErrors() throws Exception {
        mvc.perform(postJson("/api/auth/signup/email-code", "{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[0].code").isString());
    }

    @Test
    void 인증번호_확인_성공은_verifiedUntil_실패는_Error() throws Exception {
        mvc.perform(postJson("/api/auth/signup/email-code", "{\"email\":\"c@x.com\"}"));
        String code = codeIn(awaitMails(1)[0]);
        String wrong = code.equals("000000") ? "111111" : "000000";
        mvc.perform(postJson("/api/auth/signup/email-code/verify", "{\"email\":\"c@x.com\",\"code\":\"" + wrong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_MISMATCH"))
                .andExpect(jsonPath("$.message").isString());
        mvc.perform(postJson("/api/auth/signup/email-code/verify", "{\"email\":\"c@x.com\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedUntil").value(matchesPattern(
                        "2026-10-08T09:30:00\\+09:00")));
        mvc.perform(postJson("/api/auth/signup/email-code/verify", "{\"email\":\"c@x.com\",\"code\":\"12ab56\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 닉네임_확인은_available과_reason() throws Exception {
        mvc.perform(get("/api/members/nickname-availability").param("nickname", "좋은닉"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason").value(nullValue()));
        mvc.perform(get("/api/members/nickname-availability").param("nickname", "admin"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("RESERVED"));
        mvc.perform(get("/api/members/nickname-availability").param("nickname", "a"))
                .andExpect(jsonPath("$.reason").value("INVALID_FORMAT"));
        jdbc.update("INSERT INTO users (email, password_hash, name, nickname, phone) "
                + "VALUES ('o@x.com', 'h', '다른이', 'Taken', '01012345678')");
        mvc.perform(get("/api/members/nickname-availability").param("nickname", "taken"))
                .andExpect(jsonPath("$.reason").value("DUPLICATE"));
    }

    @Test
    void 가입_완료_오류_코드() throws Exception {
        mvc.perform(postJson("/api/auth/signup", "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(postJson("/api/auth/signup", com.mainblog.support.AuthTestSupport.signupJson("c@x.com", "닉네임")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }
}
