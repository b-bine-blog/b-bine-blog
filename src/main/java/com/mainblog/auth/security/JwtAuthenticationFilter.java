package com.mainblog.auth.security;

import com.mainblog.member.domain.User;
import com.mainblog.member.domain.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * AT 쿠키로 로그인한 회원을 알아낸다 (T041, FR-027).
 * 토큰이 맞아도 DB에서 ACTIVE인지 다시 봐서 탈퇴하면 바로 막는다. 블로그별 역할은 넣지 않는다(헌장 II).
 * 같은 조회에서 읽은 users.role이 ADMIN이면 ADMIN 권한을 붙인다. 토큰에는 role이 없어서
 * DB에서 role을 USER로 되돌리면 다음 요청부터 관리자 API가 막힌다(005 T006, FR-002, SEC-11, research R-02).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 메인 관리자 권한 이름. SecurityConfig가 /api/admin/**에 쓴다. */
    public static final String ADMIN = "ADMIN";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, UserRepository userRepository) {
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        jwtProvider.parse(AuthCookies.read(request, AuthCookies.ACCESS))
                .flatMap(userRepository::findById)
                .filter(User::isActive)
                .ifPresent(user -> SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(new AuthenticatedMember(user.getId()), null,
                                authorities(user))));
        chain.doFilter(request, response);
    }

    private static List<GrantedAuthority> authorities(User user) {
        return user.isAdmin() ? List.of(new SimpleGrantedAuthority(ADMIN)) : List.of();
    }
}
