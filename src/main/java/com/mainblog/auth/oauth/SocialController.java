package com.mainblog.auth.oauth;

import com.mainblog.auth.application.TokenService.IssuedTokens;
import com.mainblog.auth.oauth.SocialLoginService.PendingView;
import com.mainblog.auth.oauth.SocialLoginService.SignedIn;
import com.mainblog.auth.oauth.SocialLoginService.SignupInput;
import com.mainblog.auth.security.AuthCookies;
import com.mainblog.common.config.AuthProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.ratelimit.ClientIpResolver;
import com.mainblog.common.ratelimit.RateLimiter;
import com.mainblog.member.application.SessionMemberQuery;
import com.mainblog.member.application.SessionMemberQuery.SessionMember;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 소셜 가입·연결 대기 API (008, contracts/social-login-api.yaml). 대기 토큰은 본문으로만 받는다. */
@RestController
@RequestMapping("/api/social")
public class SocialController {

    private final SocialLoginService service;
    private final SocialClients clients;
    private final SessionMemberQuery sessionMemberQuery;
    private final AuthCookies cookies;
    private final ClientIpResolver ipResolver;
    private final RateLimiter rateLimiter;
    private final AuthProperties authProperties;

    public SocialController(SocialLoginService service, SocialClients clients, SessionMemberQuery sessionMemberQuery,
                            AuthCookies cookies, ClientIpResolver ipResolver, RateLimiter rateLimiter,
                            AuthProperties authProperties) {
        this.service = service;
        this.clients = clients;
        this.sessionMemberQuery = sessionMemberQuery;
        this.cookies = cookies;
        this.ipResolver = ipResolver;
        this.rateLimiter = rateLimiter;
        this.authProperties = authProperties;
    }

    /** 키가 있는 서비스만 버튼을 보인다(FR-014). */
    @GetMapping("/providers")
    public ProvidersResponse providers() {
        return new ProvidersResponse(clients.providers().stream().map(SocialProvider::registrationId).toList());
    }

    @PostMapping("/pending")
    public PendingView pending(@Valid @RequestBody TokenRequest body) {
        return service.view(body.token());
    }

    @PostMapping("/email-code")
    public ResponseEntity<Void> emailCode(@Valid @RequestBody EmailCodeRequest body, HttpServletRequest request) {
        limit(request);
        service.sendEmailCode(body.token(), body.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email-verify")
    public PendingView emailVerify(@Valid @RequestBody EmailVerifyRequest body) {
        return service.verifyEmailCode(body.token(), body.email(), body.code());
    }

    @PostMapping("/signup")
    public SessionMember signup(@Valid @RequestBody SignupRequest body, HttpServletResponse response) {
        SignedIn result = service.signUp(body.token(), new SignupInput(body.name(), body.nickname(), body.phone(),
                body.agreeTerms(), body.agreePrivacy()));
        write(response, result.tokens());
        return sessionMemberQuery.find(result.memberId()).orElseThrow();
    }

    @PostMapping("/link")
    public SessionMember link(@Valid @RequestBody LinkRequest body, HttpServletRequest request,
                              HttpServletResponse response) {
        SignedIn result = service.link(body.token(), body.password(), body.captchaToken(),
                ipResolver.resolve(request));
        write(response, result.tokens());
        return sessionMemberQuery.find(result.memberId()).orElseThrow();
    }

    private void limit(HttpServletRequest request) {
        AuthProperties.Window window = authProperties.rateLimit().login();
        String key = "social:" + ipResolver.resolve(request);
        if (!rateLimiter.tryAcquire(key, window.limit(), window.window())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED)
                    .with("retryAfterSeconds", rateLimiter.retryAfterSeconds(key, window.window()));
        }
    }

    private void write(HttpServletResponse response, IssuedTokens tokens) {
        cookies.write(response, tokens.accessToken(), tokens.refreshToken(), tokens.rememberMe(),
                tokens.refreshRemaining());
    }

    public record ProvidersResponse(List<String> providers) {}

    public record TokenRequest(@NotBlank @Size(max = 100) String token) {}

    public record EmailCodeRequest(@NotBlank @Size(max = 100) String token, @NotBlank @Size(max = 255) String email) {}

    public record EmailVerifyRequest(@NotBlank @Size(max = 100) String token, @NotBlank @Size(max = 255) String email,
                                     @NotBlank @Size(max = 6) String code) {}

    public record SignupRequest(@NotBlank @Size(max = 100) String token, @Size(max = 50) String name,
                                @Size(max = 30) String nickname, @Size(max = 20) String phone,
                                Boolean agreeTerms, Boolean agreePrivacy) {}

    public record LinkRequest(@NotBlank @Size(max = 100) String token, @Size(max = 100) String password,
                              @Size(max = 4096) String captchaToken) {}
}
