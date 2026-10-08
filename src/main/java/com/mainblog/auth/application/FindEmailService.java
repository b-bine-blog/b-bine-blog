package com.mainblog.auth.application;

import com.mainblog.auth.domain.PasswordResetToken;
import com.mainblog.auth.domain.PasswordResetTokenRepository;
import com.mainblog.auth.security.CodeHasher;
import com.mainblog.common.config.AuthProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.masking.EmailMasker;
import com.mainblog.common.ratelimit.RateLimiter;
import com.mainblog.member.application.MemberInputNormalizer;
import com.mainblog.member.domain.User;
import com.mainblog.member.domain.UserRepository;
import com.mainblog.member.domain.UserStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 찾기 (T069, USR-08, FR-034~FR-039, D-23, D-26).
 * 이름(앞뒤 공백 제거)과 전화번호(숫자만)로 ACTIVE 계정을 찾아 가린 이메일·가입일·임시 토큰을 준다.
 * 재설정은 회원 번호가 아니라 그 토큰으로만 받는다.
 */
@Service
public class FindEmailService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final ResetTokenResolver resetTokenResolver;
    private final PasswordResetService passwordResetService;
    private final MemberInputNormalizer normalizer;
    private final CodeHasher codeHasher;
    private final RateLimiter rateLimiter;
    private final AuthProperties properties;
    private final Clock clock;

    public FindEmailService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                            ResetTokenResolver resetTokenResolver, PasswordResetService passwordResetService,
                            MemberInputNormalizer normalizer, CodeHasher codeHasher, RateLimiter rateLimiter,
                            AuthProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.resetTokenResolver = resetTokenResolver;
        this.passwordResetService = passwordResetService;
        this.normalizer = normalizer;
        this.codeHasher = codeHasher;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
    }

    /** 같은 IP는 10분에 5번까지(FR-039). 형식이 맞지 않는 이름·전화번호는 일치하는 계정이 없는 것과 같다. */
    @Transactional
    public List<FoundAccount> find(String rawName, String rawPhone, String clientIp) {
        AuthProperties.Window limit = properties.rateLimit().findEmail();
        String ipKey = "find-email:" + clientIp;
        if (!rateLimiter.tryAcquire(ipKey, limit.limit(), limit.window())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED)
                    .with("retryAfterSeconds", rateLimiter.retryAfterSeconds(ipKey, limit.window()));
        }
        String name;
        String phone;
        try {
            name = normalizer.name(rawName);
            phone = normalizer.phone(rawPhone);
        } catch (InvalidInputException e) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plus(properties.findEmail().tokenTtl());
        return userRepository.findAllByNameAndPhoneAndStatus(name, phone, UserStatus.ACTIVE).stream()
                .sorted(Comparator.comparing(User::getId))
                .map(user -> {
                    String token = codeHasher.newRandomToken();
                    tokenRepository.save(new PasswordResetToken(user.getId(), codeHasher.sha256(token), expiresAt));
                    return new FoundAccount(EmailMasker.mask(user.getEmail()),
                            user.getCreatedAt() == null ? null : user.getCreatedAt().toLocalDate(), token);
                })
                .toList();
    }

    /** 결과의 [비밀번호 재설정]: 토큰의 계정으로만 인증번호를 보낸다(FR-037, FR-038). 만료·없음이면 410. */
    @Transactional
    public void sendResetCode(String resetToken) {
        User user = resetTokenResolver.resolve(resetToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_EXPIRED));
        passwordResetService.sendCodeTo(user);
    }

    /** 응답 한 줄. 원래 이메일은 담지 않는다(FR-036, SC-006). */
    public record FoundAccount(String maskedEmail, LocalDate joinedOn, String resetToken) {}
}
