package com.mainblog.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.mainblog.blog.BlogTestBase;
import org.junit.jupiter.api.Test;

/** 운영 규칙 안내 (007, D-114). 로그인 없이 실제 설정값(application.yml 기본값)을 받는다. */
class SiteRulesIntegrationTest extends BlogTestBase {

    @Test
    void 비회원도_운영_규칙_숫자를_설정값_그대로_받는다() throws Exception {
        Res res = getAs(null, "/api/site-rules");
        assertThat(res.status()).isEqualTo(200);
        JsonNode r = res.json();
        assertThat(r.at("/account/idleMinutes").asInt()).isEqualTo(30);
        assertThat(r.at("/account/rememberDays").asInt()).isEqualTo(14);
        assertThat(r.at("/account/withdrawalDays").asInt()).isEqualTo(30);
        assertThat(r.at("/blog/publicLimit").asInt()).isEqualTo(3);
        assertThat(r.at("/blog/closingGraceDays").asInt()).isEqualTo(7);
        assertThat(r.at("/blog/closingNoticeDays").toString()).isEqualTo("[3,1]");
        assertThat(r.at("/board/deletedPurgeDays").asInt()).isEqualTo(30);
        assertThat(r.at("/board/imageMaxMegabytes").asInt()).isEqualTo(3);
        assertThat(r.at("/report/repeatAfterDays").asInt()).isEqualTo(14);
        assertThat(r.at("/inquiry/rateWindowHours").asInt()).isEqualTo(24);
        assertThat(r.at("/inquiry/retentionDays").asInt()).isEqualTo(365);
        assertThat(r.at("/inquiry/contactEmail").isNull()).isTrue();  // 테스트는 메일 계정이 비어 있음
        assertThat(r.toString()).doesNotContainIgnoringCase("secret");
    }
}
