package com.mainblog.auth.application;

import com.mainblog.auth.domain.LoginFailureRepository;
import com.mainblog.auth.domain.PasswordResetCode;
import com.mainblog.auth.domain.PasswordResetCodeRepository;
import com.mainblog.auth.domain.RefreshTokenRepository;
import com.mainblog.auth.security.CodeHasher;
import com.mainblog.common.config.AuthProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.mail.AuthMailSender;
import com.mainblog.member.application.MemberInputNormalizer;
import com.mainblog.member.application.PasswordPolicy;
import com.mainblog.member.domain.User;
import com.mainblog.member.domain.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 찾기·재설정 (T054, USR-06, FR-030~FR-033, D-25, D-97).
 * 대상 회원은 이메일(비밀번호 찾기) 또는 이메일 찾기 임시 토큰(US6)으로 정한다. 어느 쪽이든 그 뒤 절차는 같다.
 */
@Service
public class PasswordResetService {

    private final PasswordResetCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginFailureRepository loginFailureRepository;
    private final ResetTokenResolver resetTokenResolver;
    private final CodeHasher codeHasher;
    private final AuthMailSender mailSender;
    private final MemberInputNormalizer normalizer;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    public PasswordResetService(PasswordResetCodeRepository codeRepository, UserRepository userRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                LoginFailureRepository loginFailureRepository, ResetTokenResolver resetTokenResolver,
                                CodeHasher codeHasher, AuthMailSender mailSender, MemberInputNormalizer normalizer,
                                PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder,
                                AuthProperties properties, Clock clock) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginFailureRepository = loginFailureRepository;
        this.resetTokenResolver = resetTokenResolver;
        this.codeHasher = codeHasher;
        this.mailSender = mailSender;
        this.normalizer = normalizer;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 비밀번호 찾기 ① 발송 (FR-030, FR-031). ACTIVE 회원에게만 메일을 보내고, 가입 안 된·탈퇴한 이메일은 아무것도 하지 않는다.
     * 호출한 쪽에는 언제나 같은 결과다(D-28).
     */
    @Transactional
    public void sendCode(String rawEmail) {
        findActiveByEmail(normalizer.email(rawEmail)).ifPresent(this::sendCodeTo);
    }

    /** 대상 회원에게 새 번호를 보낸다. 1분 안에 다시 오면 메일 없이 끝낸다. 이메일 찾기(US6)도 이 메서드를 쓴다. */
    @Transactional
    public void sendCodeTo(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<PasswordResetCode> previous = codeRepository.findFirstByUserIdOrderByIdDesc(user.getId());
        if (previous.isPresent() && !previous.get().canResend(now, properties.code().resendInterval())) {
            return;
        }
        codeRepository.deleteByUserId(user.getId());
        String code = codeHasher.newSixDigitCode();
        codeRepository.save(new PasswordResetCode(user.getId(), codeHasher.hashCode(code), now,
                properties.reset().codeTtl()));
        mailSender.sendResetCode(user.getEmail(), code);
    }

    /** ② 번호가 맞는지만 확인한다(쓰지 않음). 틀리면 틀린 횟수를 저장한다(FR-031, FR-032). */
    @Transactional(noRollbackFor = BusinessException.class)
    public void verify(ResetTarget target, String code) {
        User user = resolve(target);
        checkCode(user, code);
    }

    /**
     * ③ 새 비밀번호 저장 (FR-032, FR-033). 번호를 다시 확인한 뒤 두 값 일치·비밀번호 규칙을 보고,
     * 바꾸면 번호를 지우고(1회용) 모든 기기의 RT를 폐기하고 로그인 실패 기록(잠금)을 지운다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void reset(ResetTarget target, String code, String newPassword, String newPasswordConfirm) {
        User user = resolve(target);
        PasswordResetCode resetCode = checkCode(user, code);
        if (!passwordPolicy.isValid(newPassword)) {
            throw new InvalidInputException("newPassword", "Pattern");
        }
        if (!newPassword.equals(newPasswordConfirm)) {
            throw new InvalidInputException("newPasswordConfirm", "Mismatch");
        }
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        codeRepository.delete(resetCode);
        refreshTokenRepository.revokeAllByUserId(user.getId());
        loginFailureRepository.deleteByEmail(user.getEmail());
        if (target.resetToken() != null) {
            resetTokenResolver.consume(target.resetToken());
        }
    }

    private PasswordResetCode checkCode(User user, String code) {
        LocalDateTime now = LocalDateTime.now(clock);
        int maxFail = properties.code().maxFail();
        PasswordResetCode resetCode = codeRepository.findFirstByUserIdOrderByIdDesc(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CODE_INVALID));
        if (resetCode.isUnusable(now, maxFail)) {
            codeRepository.delete(resetCode);
            throw new BusinessException(ErrorCode.CODE_INVALID);
        }
        if (!codeHasher.matchesCode(code, resetCode.getCodeHash())) {
            resetCode.recordFailure();
            if (resetCode.getFailCount() >= maxFail) {
                codeRepository.delete(resetCode);
                throw new BusinessException(ErrorCode.CODE_INVALID);
            }
            throw new BusinessException(ErrorCode.CODE_MISMATCH)
                    .with("remainingAttempts", maxFail - resetCode.getFailCount());
        }
        return resetCode;
    }

    /**
     * 이메일로 대상을 찾지 못하면 번호가 없는 것과 같은 CODE_INVALID로 답해 가입 여부를 드러내지 않는다.
     * 임시 토큰이 만료됐거나 없으면 410 TOKEN_EXPIRED로 이메일 찾기를 다시 하게 한다(FR-038).
     */
    private User resolve(ResetTarget target) {
        if (target.resetToken() != null) {
            return resetTokenResolver.resolve(target.resetToken())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_EXPIRED));
        }
        return findActiveByEmail(normalizer.email(target.email()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CODE_INVALID));
    }

    private Optional<User> findActiveByEmail(String email) {
        return userRepository.findByEmail(email).filter(User::isActive);
    }

    /** 이메일 또는 이메일 찾기 임시 토큰 중 하나. */
    public record ResetTarget(String email, String resetToken) {}
}
