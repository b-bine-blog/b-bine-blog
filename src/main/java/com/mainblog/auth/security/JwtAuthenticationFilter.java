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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * AT 쿠키로 로그인한 회원을 알아낸다 (T041, FR-027).
 * 토큰이 맞아도 DB에서 ACTIVE인지 다시 봐서 탈퇴하면 바로 막는다. 역할은 넣지 않는다(헌장 II).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
                .filter(id -> userRepository.findById(id).map(User::isActive).orElse(false))
                .ifPresent(id -> SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(new AuthenticatedMember(id), null, List.of())));
        chain.doFilter(request, response);
    }
}
