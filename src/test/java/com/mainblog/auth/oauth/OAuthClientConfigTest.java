package com.mainblog.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.mainblog.common.config.AppProperties;
import com.mainblog.common.config.OAuthProperties;
import com.mainblog.common.config.OAuthProperties.Client;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

/** 앱 등록 정보 (008 FR-014, FR-016, D-125). */
class OAuthClientConfigTest {

    private static SocialClients clients(Client google, Client kakao, Client naver) {
        OAuthProperties props = new OAuthProperties(google, kakao, naver, Duration.ofMinutes(10),
                Duration.ofMinutes(30), Duration.ofMinutes(10));
        return new OAuthClientConfig().socialClients(props, new AppProperties("https://mainttblog.java21.net"));
    }

    @Test
    void 키가_있는_서비스만_등록하고_돌아올_주소는_사이트_주소_기준이다() {
        Client none = new Client("", "");
        SocialClients c = clients(none, new Client("kakao-id", "kakao-secret"), none);
        assertThat(c.providers()).containsExactly(SocialProvider.KAKAO);
        ClientRegistration kakao = c.repository().findByRegistrationId("kakao");
        assertThat(kakao.getRedirectUri()).isEqualTo("https://mainttblog.java21.net/login/oauth2/code/kakao");
    }

    @Test
    void 카카오는_범위를_요청하지_않아_이메일_권한이_없는_앱도_로그인된다() {
        Client none = new Client("", "");
        ClientRegistration kakao = clients(none, new Client("kakao-id", "kakao-secret"), none).repository()
                .findByRegistrationId("kakao");
        assertThat(kakao.getScopes()).isNullOrEmpty();
    }
}
