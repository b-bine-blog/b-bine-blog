package com.mainblog.auth.oauth;

import com.mainblog.common.config.AppProperties;
import com.mainblog.common.config.OAuthProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

/**
 * 구글·카카오·네이버 앱 등록 정보 (008 FR-001, FR-014, D-125).
 * 키가 있는 서비스만 등록한다. 돌아올 주소는 사이트 주소(app.base-url) 기준이라 nginx 뒤에서도 그대로 맞다.
 * 카카오·네이버는 Spring에 기본 정보가 없어 인가·토큰·사용자 정보 주소를 직접 적는다.
 */
@Configuration
public class OAuthClientConfig {

    @Bean
    public SocialClients socialClients(OAuthProperties properties, AppProperties app) {
        List<ClientRegistration> list = new ArrayList<>();
        if (properties.google().configured()) {
            list.add(ClientRegistration.withRegistrationId("google")
                    .clientId(properties.google().clientId())
                    .clientSecret(properties.google().clientSecret())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(redirect(app, "google"))
                    .scope("openid", "email")
                    .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                    .tokenUri("https://oauth2.googleapis.com/token")
                    .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                    .issuerUri("https://accounts.google.com")
                    .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                    .userNameAttributeName(IdTokenClaimNames.SUB)
                    .clientName("구글")
                    .build());
        }
        if (properties.kakao().configured()) {
            list.add(ClientRegistration.withRegistrationId("kakao")
                    .clientId(properties.kakao().clientId())
                    .clientSecret(properties.kakao().clientSecret())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(redirect(app, "kakao"))
                    // 범위를 적지 않으면 카카오 콘솔의 동의항목을 그대로 묻는다. 이메일 권한이 없는 앱이
                    // account_email을 요청하면 KOE205로 로그인이 막혀서 적지 않는다(FR-016, D-125)
                    .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                    .tokenUri("https://kauth.kakao.com/oauth/token")
                    .userInfoUri("https://kapi.kakao.com/v2/user/me")
                    .userNameAttributeName("id")
                    .clientName("카카오")
                    .build());
        }
        if (properties.naver().configured()) {
            list.add(ClientRegistration.withRegistrationId("naver")
                    .clientId(properties.naver().clientId())
                    .clientSecret(properties.naver().clientSecret())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(redirect(app, "naver"))
                    .scope("email")
                    .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
                    .tokenUri("https://nid.naver.com/oauth2.0/token")
                    .userInfoUri("https://openapi.naver.com/v1/nid/me")
                    .userNameAttributeName("response")
                    .clientName("네이버")
                    .build());
        }
        return new SocialClients(list);
    }

    private static String redirect(AppProperties app, String id) {
        return app.absolute("/login/oauth2/code/" + id);
    }
}
