package com.mainblog.auth.application;

import com.mainblog.auth.domain.LoginFailure;
import com.mainblog.auth.domain.LoginFailureRepository;
import com.mainblog.auth.application.TokenService.IssuedTokens;
import com.mainblog.common.config.AuthProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.ratelimit.RateLimiter;
import com.mainblog.member.application.MemberInputNormalizer;
import com.mainblog.member.domain.User;
import com.mainblog.member.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 (T040, FR-018, FR-020~FR-022, FR-028, D-28, D-97).
 * 실패 횟수는 입력한 이메일 기준이라 없는 이메일도 같은 안내(3번째부터 사람 확인, 5번째 5분 잠금)를 받는다.
 * 블로그 정지는 로그인을 막지 않는다.
 */
@Service
public class LoginService {

    private final LoginFailureRepository failureRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CaptchaVerifier captchaVerifier;
    private final RateLimiter rateLimiter;
    private final TokenService tokenService;
    private final MemberInputNormalizer normalizer;
    private final AuthProperties properties;
    private final Clock clock;
    /** 없는 이메일도 bcrypt 비교를 한 번 해서 응답 시간으로 가입 여부가 드러나지 않게 한다(D-28). */
    private final String dummyHash;

    public LoginService(LoginFailureRepository failureRepository, UserRepository userRepository,
                        PasswordEncoder passwordEncoder, CaptchaVerifier captchaVerifier, RateLimiter rateLimiter,
                        TokenService tokenService, MemberInputNormalizer normalizer, AuthProperties properties,
                        Clock clock) {
        this.failureRepository = failureRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.captchaVerifier = captchaVerifier;
        this.rateLimiter = rateLimiter;
        this.tokenService = tokenService;
        this.normalizer = normalizer;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResult login(LoginCommand command) {
        AuthProperties.Login rules = properties.login();
        AuthProperties.Window limit = properties.rateLimit().login();
        String ipKey = "login:" + command.clientIp();
        if (!rateLimiter.tryAcquire(ipKey, limit.limit(), limit.window())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED)
                    .with("retryAfterSeconds", rateLimiter.retryAfterSeconds(ipKey, limit.window()));
        }

        String email = normalizer.email(command.email());
        LocalDateTime now = LocalDateTime.now(clock);

        Optional<LoginFailure> failure = failureRepository.findByEmail(email);
        if (failure.isPresent() && failure.get().isLocked(now)) {
            throw locked(failure.get().getLockedUntil(), now);
        }
        if (failure.isPresent() && failure.get().isLockExpired(now)) {
            failureRepository.deleteByEmail(email);
            failure = Optional.empty();
        }

        int failCount = failure.map(LoginFailure::getFailCount).orElse(0);
        if (failCount >= rules.captchaAfter()) {
            if (command.captchaToken() == null || command.captchaToken().isBlank()) {
                throw new BusinessException(ErrorCode.CAPTCHA_REQUIRED).with("captchaRequired", true);
            }
            if (!captchaVerifier.verify(command.captchaToken(), command.clientIp())) {
                throw new BusinessException(ErrorCode.CAPTCHA_FAILED).with("captchaRequired", true);
            }
        }

        Optional<User> user = userRepository.findByEmail(email).filter(User::isActive);
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(command.password() == null ? "" : command.password(), hash);
        if (user.isEmpty() || !matches) {
            throw recordFailure(email, now, rules);
        }

        failureRepository.deleteByEmail(email);
        IssuedTokens tokens = tokenService.issue(user.get().getId(), command.rememberMe());
        return new LoginResult(user.get().getId(), tokens);
    }

    private BusinessException recordFailure(String email, LocalDateTime now, AuthProperties.Login rules) {
        LocalDateTime lockedUntil = now.plus(rules.lockDuration());
        failureRepository.recordFailure(email, now, rules.lockAfter(), lockedUntil);
        LoginFailure after = failureRepository.findByEmail(email).orElseThrow();
        if (after.isLocked(now)) {
            return locked(after.getLockedUntil(), now);
        }
        return new BusinessException(ErrorCode.INVALID_CREDENTIALS)
                .with("captchaRequired", after.getFailCount() >= rules.captchaAfter());
    }

    private BusinessException locked(LocalDateTime lockedUntil, LocalDateTime now) {
        long seconds = Math.max(1, Duration.between(now, lockedUntil).toSeconds());
        return new BusinessException(ErrorCode.LOCKED)
                .with("captchaRequired", true)
                .with("retryAfterSeconds", seconds);
    }

    public record LoginCommand(String email, String password, boolean rememberMe, String captchaToken,
                               String clientIp) {}

    public record LoginResult(Long memberId, IssuedTokens tokens) {}
}
