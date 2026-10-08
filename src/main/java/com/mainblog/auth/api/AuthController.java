package com.mainblog.auth.api;

import com.mainblog.auth.application.LoginService;
import com.mainblog.auth.application.LoginService.LoginCommand;
import com.mainblog.auth.application.LoginService.LoginResult;
import com.mainblog.auth.application.TokenService;
import com.mainblog.auth.application.TokenService.IssuedTokens;
import com.mainblog.auth.security.AuthCookies;
import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.ratelimit.ClientIpResolver;
import com.mainblog.member.application.SessionMemberQuery;
import com.mainblog.member.application.SessionMemberQuery.SessionMember;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인·로그아웃·로그인 유지 API (T042). 계약: contracts의 login 태그. 토큰은 쿠키로만 오간다(FR-026). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginService loginService;
    private final TokenService tokenService;
    private final SessionMemberQuery sessionMemberQuery;
    private final AuthCookies cookies;
    private final ClientIpResolver ipResolver;

    public AuthController(LoginService loginService, TokenService tokenService, SessionMemberQuery sessionMemberQuery,
                          AuthCookies cookies, ClientIpResolver ipResolver) {
        this.loginService = loginService;
        this.tokenService = tokenService;
        this.sessionMemberQuery = sessionMemberQuery;
        this.cookies = cookies;
        this.ipResolver = ipResolver;
    }

    @PostMapping("/login")
    public SessionMember login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                               HttpServletResponse response) {
        LoginResult result = loginService.login(new LoginCommand(body.email(), body.password(),
                Boolean.TRUE.equals(body.rememberMe()), body.captchaToken(), ipResolver.resolve(request)));
        write(response, result.tokens());
        return sessionMemberQuery.find(result.memberId()).orElseThrow();
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            write(response, tokenService.refresh(AuthCookies.read(request, AuthCookies.REFRESH)));
        } catch (BusinessException e) {
            cookies.clear(response);
            throw e;
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/extend")
    public ResponseEntity<Void> extend(HttpServletRequest request) {
        tokenService.extend(AuthCookies.read(request, AuthCookies.REFRESH));
        return ResponseEntity.noContent().build();
    }

    /** 로그인 상태가 아니어도 204(contracts). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        tokenService.revoke(AuthCookies.read(request, AuthCookies.REFRESH));
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    public SessionMember session(@AuthenticationPrincipal AuthenticatedMember member) {
        if (member == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return sessionMemberQuery.find(member.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }

    private void write(HttpServletResponse response, IssuedTokens tokens) {
        cookies.write(response, tokens.accessToken(), tokens.refreshToken(), tokens.rememberMe(),
                tokens.refreshRemaining());
    }

    public record LoginRequest(@NotBlank @Size(max = 255) String email,
                               @NotNull @Size(max = 100) String password,
                               @NotNull Boolean rememberMe,
                               @Size(max = 4096) String captchaToken) {}
}
