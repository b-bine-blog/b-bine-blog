package com.mainblog.auth.application;

import com.mainblog.auth.domain.RefreshToken;
import com.mainblog.auth.domain.RefreshTokenRepository;
import com.mainblog.auth.security.CodeHasher;
import com.mainblog.auth.security.JwtProvider;
import com.mainblog.common.config.AuthProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.member.domain.User;
import com.mainblog.member.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 자격 발급·갱신·폐기 (T039, FR-023, FR-024, FR-026, FR-029, R-03, R-04).
 * RT 유효 조건: revoked = false, expires_at > now, (remember_me 또는 last_active_at + 30분 > now).
 */
@Service
public class TokenService {

    /** 활동 시각을 DB에 쓰는 최소 간격(R-04). 설정값이 아닌 저장 부담 조절용 값이다. */
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final CodeHasher codeHasher;
    private final AuthProperties properties;
    private final Clock clock;

    public TokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository,
                        JwtProvider jwtProvider, CodeHasher codeHasher, AuthProperties properties, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.codeHasher = codeHasher;
        this.properties = properties;
        this.clock = clock;
    }

    /** 새 기기 로그인. 만료는 로그인 시각 + 14일로 고정한다(D-97). */
    @Transactional
    public IssuedTokens issue(Long userId, boolean rememberMe) {
        LocalDateTime now = LocalDateTime.now(clock);
        return save(userId, rememberMe, now, now.plus(properties.rememberMeTtl()), now);
    }

    /**
     * RT 회전. 만료 시각·로그인 유지 여부는 이어받는다.
     * 이미 폐기된 RT가 다시 오면 탈취로 보고 그 회원의 RT를 모두 폐기한다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public IssuedTokens refresh(String rawToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        RefreshToken current = find(rawToken).orElseThrow(TokenService::unauthenticated);
        if (current.isRevoked()) {
            refreshTokenRepository.revokeAllByUserId(current.getUserId());
            throw unauthenticated();
        }
        if (!current.isUsable(now, properties.idleTimeout()) || !isActiveUser(current.getUserId())) {
            throw unauthenticated();
        }
        current.revoke();
        return save(current.getUserId(), current.isRememberMe(), current.getLastActiveAt(), current.getExpiresAt(), now);
    }

    /** 사용자가 직접 한 행동(X-User-Activity)이면 무활동 시간을 다시 시작한다. 1분에 한 번만 쓴다. */
    @Transactional
    public void touch(String rawToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        find(rawToken)
                .filter(t -> t.isUsable(now, properties.idleTimeout()))
                .filter(t -> !t.getLastActiveAt().plus(TOUCH_INTERVAL).isAfter(now))
                .ifPresent(t -> t.touch(now));
    }

    /** 로그인 연장(FR-025). 유효한 RT여야 한다. */
    @Transactional
    public void extend(String rawToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        RefreshToken token = find(rawToken)
                .filter(t -> t.isUsable(now, properties.idleTimeout()))
                .orElseThrow(TokenService::unauthenticated);
        token.touch(now);
    }

    /** 로그아웃: 이 기기의 RT 폐기(FR-029). 없거나 이미 폐기됐어도 그대로 끝낸다. */
    @Transactional
    public void revoke(String rawToken) {
        find(rawToken).ifPresent(RefreshToken::revoke);
    }

    /**
     * 비밀번호 변경 뒤(FR-044, D-97): 그 회원의 RT를 모두 폐기하고 이 기기에만 새 RT를 준다.
     * 이 기기의 RT가 유효하면 로그인 유지 여부와 만료 시각을 이어받고, 없으면 로그인 유지 없이 새로 발급한다.
     */
    @Transactional
    public IssuedTokens reissueAfterPasswordChange(Long userId, String rawToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<RefreshToken> current = find(rawToken)
                .filter(t -> t.getUserId().equals(userId))
                .filter(t -> t.isUsable(now, properties.idleTimeout()));
        refreshTokenRepository.revokeAllByUserId(userId);
        if (current.isEmpty()) {
            return save(userId, false, now, now.plus(properties.rememberMeTtl()), now);
        }
        return save(userId, current.get().isRememberMe(), now, current.get().getExpiresAt(), now);
    }

    /** 유효한 RT의 행. 비밀번호 변경에서 지금 기기를 가릴 때 쓴다. */
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findUsable(String rawToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        return find(rawToken).filter(t -> t.isUsable(now, properties.idleTimeout()));
    }

    private Optional<RefreshToken> find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHash(codeHasher.sha256(rawToken));
    }

    private boolean isActiveUser(Long userId) {
        return userRepository.findById(userId).map(User::isActive).orElse(false);
    }

    private IssuedTokens save(Long userId, boolean rememberMe, LocalDateTime lastActiveAt, LocalDateTime expiresAt,
                              LocalDateTime now) {
        String raw = codeHasher.newRandomToken();
        RefreshToken token = new RefreshToken(userId, codeHasher.sha256(raw), rememberMe, lastActiveAt, expiresAt);
        refreshTokenRepository.save(token);
        return new IssuedTokens(jwtProvider.issue(userId), raw, rememberMe, Duration.between(now, expiresAt), token);
    }

    private static BusinessException unauthenticated() {
        return new BusinessException(ErrorCode.UNAUTHENTICATED);
    }

    /** 쿠키로 내려줄 값. 본문에는 넣지 않는다(FR-026). */
    public record IssuedTokens(String accessToken, String refreshToken, boolean rememberMe,
                               Duration refreshRemaining, RefreshToken entity) {}
}
