package com.mainblog.auth.application;

import com.mainblog.auth.domain.EmailVerification;
import com.mainblog.auth.domain.EmailVerificationRepository;
import com.mainblog.auth.security.CodeHasher;
import com.mainblog.common.config.AuthProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.mail.AuthMailSender;
import com.mainblog.member.application.MemberInputNormalizer;
import com.mainblog.member.application.NicknamePolicy;
import com.mainblog.member.application.PasswordPolicy;
import com.mainblog.member.domain.AgreementType;
import com.mainblog.member.domain.User;
import com.mainblog.member.domain.UserAgreement;
import com.mainblog.member.domain.UserAgreementRepository;
import com.mainblog.member.domain.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 3단계 (T028~T030, USR-01, USR-02).
 * ① 인증번호 발송 ② 인증번호 확인 ③ 가입 완료. 화면 응답으로 가입 여부를 알 수 없게 한다(FR-006, D-28).
 */
@Service
public class SignupService {

    private final EmailVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final UserAgreementRepository agreementRepository;
    private final CodeHasher codeHasher;
    private final AuthMailSender mailSender;
    private final MemberInputNormalizer normalizer;
    private final PasswordPolicy passwordPolicy;
    private final NicknamePolicy nicknamePolicy;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    public SignupService(EmailVerificationRepository verificationRepository, UserRepository userRepository,
                         UserAgreementRepository agreementRepository, CodeHasher codeHasher,
                         AuthMailSender mailSender, MemberInputNormalizer normalizer, PasswordPolicy passwordPolicy,
                         NicknamePolicy nicknamePolicy, PasswordEncoder passwordEncoder, AuthProperties properties,
                         Clock clock) {
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
        this.agreementRepository = agreementRepository;
        this.codeHasher = codeHasher;
        this.mailSender = mailSender;
        this.normalizer = normalizer;
        this.passwordPolicy = passwordPolicy;
        this.nicknamePolicy = nicknamePolicy;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * ① 인증번호 발송 (FR-003, FR-005, FR-006).
     * 가입된 이메일이면 안내 메일만, 아니면 새 번호를 만들어 이전 번호를 무효로 한다.
     * 1분 안에 다시 오면 메일 없이 끝낸다. 어느 경우든 호출한 쪽에는 같은 결과(반환값 없음)다.
     */
    @Transactional
    public void sendCode(String rawEmail) {
        String email = normalizer.email(rawEmail);
        LocalDateTime now = LocalDateTime.now(clock);

        if (userRepository.findByEmail(email).isPresent()) {
            mailSender.sendAlreadyRegistered(email);
            return;
        }

        EmailVerification verification = verificationRepository.findByEmail(email)
                .orElseGet(() -> new EmailVerification(email));
        if (!verification.canResend(now, properties.code().resendInterval())) {
            return;
        }
        String code = codeHasher.newSixDigitCode();
        verification.issue(codeHasher.hashCode(code), now, properties.signup().codeTtl());
        verificationRepository.save(verification);
        mailSender.sendSignupCode(email, code);
    }

    /**
     * ② 인증번호 확인 (FR-004, FR-007).
     * 틀린 횟수는 오류 응답과 함께 저장해야 하므로 BusinessException으로 되돌리지 않는다.
     *
     * @return 이 시각까지 가입을 끝내야 한다(인증 시각 + 30분, D-97)
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public LocalDateTime verifyCode(String rawEmail, String code) {
        String email = normalizer.email(rawEmail);
        LocalDateTime now = LocalDateTime.now(clock);
        int maxFail = properties.code().maxFail();

        EmailVerification verification = verificationRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.CODE_INVALID));
        if (verification.isUnusable(now, maxFail)) {
            verificationRepository.delete(verification);
            throw new BusinessException(ErrorCode.CODE_INVALID);
        }
        if (!codeHasher.matchesCode(code, verification.getCodeHash())) {
            verification.recordFailure();
            if (verification.getFailCount() >= maxFail) {
                verificationRepository.delete(verification);
                throw new BusinessException(ErrorCode.CODE_INVALID);
            }
            throw new BusinessException(ErrorCode.CODE_MISMATCH)
                    .with("remainingAttempts", maxFail - verification.getFailCount());
        }
        verification.markVerified(now);
        return now.plus(properties.signup().verifiedTtl());
    }

    /**
     * ③ 가입 완료 (FR-001, FR-002, FR-007~FR-014).
     * 인증 완료 여부를 다시 확인해 단계 건너뛰기를 막고, 중복은 DB UNIQUE로 동시 요청에서도 막는다(SC-009).
     */
    @Transactional
    public Long signUp(SignupCommand command) {
        String email = normalizer.email(command.email());
        LocalDateTime now = LocalDateTime.now(clock);

        Optional<EmailVerification> verification = verificationRepository.findByEmail(email);
        if (verification.isEmpty() || !verification.get().isVerifiedWithin(now, properties.signup().verifiedTtl())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (!passwordPolicy.isValid(command.password())) {
            throw new InvalidInputException("password", "Pattern");
        }
        String name = normalizer.name(command.name());
        String phone = normalizer.phone(command.phone());
        String nickname = command.nickname() == null ? null : command.nickname().strip();
        switch (nicknamePolicy.check(nickname)) {
            case INVALID_FORMAT -> throw new InvalidInputException("nickname", "Pattern");
            case RESERVED -> throw new InvalidInputException("nickname", "Reserved");
            case DUPLICATE -> throw new BusinessException(ErrorCode.NICKNAME_DUPLICATE);
            case AVAILABLE -> { }
        }
        if (!Boolean.TRUE.equals(command.agreeTerms())) {
            throw new InvalidInputException("agreeTerms", "AssertTrue");
        }
        if (!Boolean.TRUE.equals(command.agreePrivacy())) {
            throw new InvalidInputException("agreePrivacy", "AssertTrue");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.SIGNUP_UNAVAILABLE);
        }

        User user = User.signUp(email, passwordEncoder.encode(command.password()), name, nickname, phone);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw duplicateError(e);
        }
        agreementRepository.save(new UserAgreement(user.getId(), AgreementType.TERMS, now));
        agreementRepository.save(new UserAgreement(user.getId(), AgreementType.PRIVACY, now));
        verificationRepository.delete(verification.get());
        return user.getId();
    }

    private static BusinessException duplicateError(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        if (message.contains("uk_users_nickname")) {
            return new BusinessException(ErrorCode.NICKNAME_DUPLICATE);
        }
        return new BusinessException(ErrorCode.SIGNUP_UNAVAILABLE);
    }

    public record SignupCommand(String email, String password, String name, String nickname, String phone,
                                Boolean agreeTerms, Boolean agreePrivacy) {}
}
