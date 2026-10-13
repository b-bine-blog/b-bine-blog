package com.mainblog.auth.oauth;

import com.mainblog.auth.application.LoginService;
import com.mainblog.auth.application.LoginService.LoginCommand;
import com.mainblog.auth.application.SignupService;
import com.mainblog.auth.application.TokenService;
import com.mainblog.auth.application.TokenService.IssuedTokens;
import com.mainblog.auth.domain.EmailVerification;
import com.mainblog.auth.domain.EmailVerificationRepository;
import com.mainblog.auth.oauth.SocialAccountStore.Connection;
import com.mainblog.auth.oauth.SocialPendingStore.Pending;
import com.mainblog.auth.oauth.SocialPendingStore.Purpose;
import com.mainblog.auth.security.CodeHasher;
import com.mainblog.common.config.AuthProperties;
import com.mainblog.common.config.OAuthProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.mail.AuthMailSender;
import com.mainblog.common.masking.EmailMasker;
import com.mainblog.member.application.MemberInputNormalizer;
import com.mainblog.member.application.NicknamePolicy;
import com.mainblog.member.domain.AgreementType;
import com.mainblog.member.domain.User;
import com.mainblog.member.domain.UserAgreement;
import com.mainblog.member.domain.UserAgreementRepository;
import com.mainblog.member.domain.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 (008, USR-09, D-125).
 * 소셜 서비스에서 돌아오면 고유 번호로 회원을 찾고(FR-002), 없으면 가입 ③단계(FR-003)나
 * 같은 이메일 회원의 비밀번호 확인(FR-005), 이메일 인증(FR-016)을 기다리는 대기 정보를 만든다.
 */
@Service
public class SocialLoginService {

    private final SocialAccountStore accounts;
    private final SocialPendingStore pendings;
    private final UserRepository userRepository;
    private final UserAgreementRepository agreementRepository;
    private final EmailVerificationRepository verificationRepository;
    private final TokenService tokenService;
    private final LoginService loginService;
    private final SignupService signupService;
    private final CodeHasher codeHasher;
    private final AuthMailSender mailSender;
    private final MemberInputNormalizer normalizer;
    private final NicknamePolicy nicknamePolicy;
    private final AuthProperties authProperties;
    private final OAuthProperties properties;
    private final Clock clock;

    public SocialLoginService(SocialAccountStore accounts, SocialPendingStore pendings, UserRepository userRepository,
                              UserAgreementRepository agreementRepository,
                              EmailVerificationRepository verificationRepository, TokenService tokenService,
                              LoginService loginService, SignupService signupService, CodeHasher codeHasher,
                              AuthMailSender mailSender, MemberInputNormalizer normalizer,
                              NicknamePolicy nicknamePolicy, AuthProperties authProperties,
                              OAuthProperties properties, Clock clock) {
        this.accounts = accounts;
        this.pendings = pendings;
        this.userRepository = userRepository;
        this.agreementRepository = agreementRepository;
        this.verificationRepository = verificationRepository;
        this.tokenService = tokenService;
        this.loginService = loginService;
        this.signupService = signupService;
        this.codeHasher = codeHasher;
        this.mailSender = mailSender;
        this.normalizer = normalizer;
        this.nicknamePolicy = nicknamePolicy;
        this.authProperties = authProperties;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 소셜 서비스에서 돌아왔을 때 (FR-002, FR-003, FR-005, FR-010, FR-011, FR-016).
     *
     * @param linkUserId 내 정보에서 연결을 시작한 회원. 로그인이면 null
     */
    @Transactional
    public CallbackResult onCallback(SocialProfile profile, boolean rememberMe, boolean linkMode, Long linkUserId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<User> linked = accounts.findUserId(profile.provider(), profile.providerUserId())
                .flatMap(userRepository::findById);

        if (linkMode) {
            if (linkUserId == null) {
                return CallbackResult.error(ErrorCode.UNAUTHENTICATED);
            }
            User me = userRepository.findById(linkUserId).filter(User::isActive)
                    .orElse(null);
            if (me == null) {
                return CallbackResult.error(ErrorCode.UNAUTHENTICATED);
            }
            if (linked.isPresent()) {
                return linked.get().getId().equals(me.getId())
                        ? CallbackResult.linked(me.getId())
                        : CallbackResult.error(ErrorCode.SOCIAL_ALREADY_LINKED);
            }
            if (me.isAdmin()) {
                return CallbackResult.error(ErrorCode.SOCIAL_NOT_ALLOWED);
            }
            try {
                accounts.insert(me.getId(), profile.provider(), profile.providerUserId(), profile.verifiedEmail());
            } catch (DataIntegrityViolationException e) {
                // 같은 서비스가 이미 연결돼 있거나, 그 사이 다른 회원이 연결했다
                return CallbackResult.error(ErrorCode.SOCIAL_ALREADY_LINKED);
            }
            return CallbackResult.linked(me.getId());
        }

        if (linked.isPresent()) {
            User user = linked.get();
            if (user.isActive()) {
                if (user.isAdmin()) {
                    return CallbackResult.error(ErrorCode.SOCIAL_NOT_ALLOWED);
                }
                return CallbackResult.loggedIn(user.getId(), tokenService.issue(user.getId(), rememberMe));
            }
            // 탈퇴하면 연결을 지우지만(FR-012), 남아 있으면 정리하고 새 가입으로 간다
            accounts.deleteLink(profile.provider(), profile.providerUserId());
        }

        String email = profile.verifiedEmail();
        if (email != null) {
            Optional<User> sameEmail = userRepository.findByEmail(email).filter(User::isActive);
            if (sameEmail.isPresent()) {
                Optional<CallbackResult> refused = refuseLink(sameEmail.get());
                if (refused.isPresent()) {
                    return refused.get();
                }
                return pending(profile, email, Purpose.LINK, rememberMe, now.plus(properties.linkTtl()));
            }
        }
        return pending(profile, email, Purpose.SIGNUP, rememberMe, now.plus(properties.signupTtl()));
    }

    /** 대기 상태 보기: 다음 단계와 가린 이메일 (화면 social.html). */
    @Transactional
    public PendingView view(String token) {
        Pending p = require(token);
        return toView(p);
    }

    /** 이메일 직접 입력 ① 인증번호 보내기 (FR-016). 가입 여부와 관계없이 같은 응답이고, 메일은 인증번호다. */
    @Transactional
    public void sendEmailCode(String token, String rawEmail) {
        Pending p = require(token);
        if (p.purpose() != Purpose.SIGNUP || p.email() != null) {
            throw new BusinessException(ErrorCode.SOCIAL_PENDING_INVALID);
        }
        String email = normalizer.email(rawEmail);
        LocalDateTime now = LocalDateTime.now(clock);
        EmailVerification verification = verificationRepository.findByEmail(email)
                .orElseGet(() -> new EmailVerification(email));
        if (!verification.canResend(now, authProperties.code().resendInterval())) {
            return;
        }
        String code = codeHasher.newSixDigitCode();
        verification.issue(codeHasher.hashCode(code), now, authProperties.signup().codeTtl());
        verificationRepository.save(verification);
        mailSender.sendSignupCode(email, code);
    }

    /**
     * 이메일 직접 입력 ② 인증번호 확인 (FR-016). 인증한 이메일이 이미 가입된 회원의 것이면 비밀번호 확인(FR-005)으로 바꾼다.
     * 틀린 횟수는 오류와 함께 저장해야 하므로 되돌리지 않는다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public PendingView verifyEmailCode(String token, String rawEmail, String code) {
        Pending p = require(token);
        if (p.purpose() != Purpose.SIGNUP || p.email() != null) {
            throw new BusinessException(ErrorCode.SOCIAL_PENDING_INVALID);
        }
        String email = normalizer.email(rawEmail);
        signupService.verifyCode(email, code);
        verificationRepository.findByEmail(email).ifPresent(verificationRepository::delete);
        Optional<User> sameEmail = userRepository.findByEmail(email).filter(User::isActive);
        if (sameEmail.isPresent()) {
            Optional<CallbackResult> refused = refuseLink(sameEmail.get());
            if (refused.isPresent()) {
                pendings.delete(p.id());
                throw new BusinessException(refused.get().error());
            }
            LocalDateTime expires = LocalDateTime.now(clock).plus(properties.linkTtl());
            pendings.changeToLink(p.id(), email, expires);
            return toView(new Pending(p.id(), p.provider(), p.providerUserId(), email, Purpose.LINK,
                    p.rememberMe(), expires));
        }
        pendings.setEmail(p.id(), email);
        return toView(new Pending(p.id(), p.provider(), p.providerUserId(), email, Purpose.SIGNUP, p.rememberMe(),
                p.expiresAt()));
    }

    /** 가입 ③단계 마치기 (FR-003, 001 FR-010~FR-016). 비밀번호 없이 회원을 만든다. */
    @Transactional
    public SignedIn signUp(String token, SignupInput input) {
        Pending p = require(token);
        if (p.purpose() != Purpose.SIGNUP || p.email() == null) {
            throw new BusinessException(ErrorCode.SOCIAL_PENDING_INVALID);
        }
        String name = normalizer.name(input.name());
        String phone = normalizer.phone(input.phone());
        String nickname = input.nickname() == null ? null : input.nickname().strip();
        switch (nicknamePolicy.check(nickname)) {
            case INVALID_FORMAT -> throw new InvalidInputException("nickname", "Pattern");
            case RESERVED -> throw new InvalidInputException("nickname", "Reserved");
            case DUPLICATE -> throw new BusinessException(ErrorCode.NICKNAME_DUPLICATE);
            case AVAILABLE -> { }
        }
        if (!Boolean.TRUE.equals(input.agreeTerms())) {
            throw new InvalidInputException("agreeTerms", "AssertTrue");
        }
        if (!Boolean.TRUE.equals(input.agreePrivacy())) {
            throw new InvalidInputException("agreePrivacy", "AssertTrue");
        }
        if (userRepository.findByEmail(p.email()).isPresent()) {
            throw new BusinessException(ErrorCode.SIGNUP_UNAVAILABLE);
        }
        User user = User.signUp(p.email(), null, name, nickname, phone);
        try {
            userRepository.saveAndFlush(user);
            accounts.insert(user.getId(), p.provider(), p.providerUserId(), p.email());
        } catch (DataIntegrityViolationException e) {
            String message = String.valueOf(e.getMostSpecificCause().getMessage());
            if (message.contains("uk_users_nickname")) {
                throw new BusinessException(ErrorCode.NICKNAME_DUPLICATE);
            }
            throw new BusinessException(ErrorCode.SIGNUP_UNAVAILABLE);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        agreementRepository.save(new UserAgreement(user.getId(), AgreementType.TERMS, now));
        agreementRepository.save(new UserAgreement(user.getId(), AgreementType.PRIVACY, now));
        pendings.delete(p.id());
        return new SignedIn(user.getId(), tokenService.issue(user.getId(), p.rememberMe()));
    }

    /**
     * 비밀번호 확인 뒤 연결 (FR-005). 이메일 로그인과 같은 실패 횟수·사람 확인·잠금을 쓴다(LoginService).
     * 실패 기록은 오류와 함께 남아야 하므로 되돌리지 않는다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public SignedIn link(String token, String password, String captchaToken, String clientIp) {
        Pending p = require(token);
        if (p.purpose() != Purpose.LINK || p.email() == null) {
            throw new BusinessException(ErrorCode.SOCIAL_PENDING_INVALID);
        }
        User user = userRepository.findByEmail(p.email()).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIAL_PENDING_INVALID));
        refuseLink(user).ifPresent(r -> {
            throw new BusinessException(r.error());
        });
        LoginService.LoginResult result = loginService.login(
                new LoginCommand(p.email(), password, p.rememberMe(), captchaToken, clientIp));
        try {
            accounts.insert(result.memberId(), p.provider(), p.providerUserId(), p.email());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.SOCIAL_ALREADY_LINKED);
        }
        pendings.delete(p.id());
        return new SignedIn(result.memberId(), result.tokens());
    }

    /** 내 정보의 연결 상태 (FR-010). */
    @Transactional(readOnly = true)
    public Connections connections(long userId) {
        User user = activeMember(userId);
        return new Connections(user.getPasswordHash() != null, accounts.findByUser(userId));
    }

    /** 연결 끊기 (FR-010). 로그인할 방법이 하나도 남지 않으면 끊지 않는다(SC-006). */
    @Transactional
    public void unlink(long userId, SocialProvider provider) {
        User user = userRepository.findByIdForUpdate(userId).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        List<Connection> list = accounts.findByUser(userId);
        boolean has = list.stream().anyMatch(c -> c.provider() == provider);
        if (!has) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (user.getPasswordHash() == null && list.size() <= 1) {
            throw new BusinessException(ErrorCode.SOCIAL_LAST_LOGIN_METHOD);
        }
        accounts.delete(userId, provider);
    }

    /** 탈퇴하면 소셜 연결을 바로 지운다 (FR-012). */
    @Transactional
    public void onWithdraw(long userId) {
        accounts.deleteAllByUser(userId);
    }


    /** 관리자 계정은 소셜을 쓰지 않고(SEC-09), 비밀번호가 없는 회원은 비밀번호로 확인할 수 없다(FR-005). */
    private static Optional<CallbackResult> refuseLink(User user) {
        if (user.isAdmin()) {
            return Optional.of(CallbackResult.error(ErrorCode.SOCIAL_NOT_ALLOWED));
        }
        if (user.getPasswordHash() == null) {
            return Optional.of(CallbackResult.error(ErrorCode.SOCIAL_LINK_FROM_ME));
        }
        return Optional.empty();
    }

    private CallbackResult pending(SocialProfile profile, String email, Purpose purpose, boolean rememberMe,
                                   LocalDateTime expiresAt) {
        String token = codeHasher.newRandomToken();
        pendings.create(codeHasher.sha256(token), profile.provider(), profile.providerUserId(), email, purpose,
                rememberMe, expiresAt);
        return CallbackResult.pending(token);
    }

    private Pending require(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw new BusinessException(ErrorCode.SOCIAL_PENDING_INVALID);
        }
        return pendings.findValidForUpdate(codeHasher.sha256(token), LocalDateTime.now(clock))
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIAL_PENDING_INVALID));
    }

    private User activeMember(long userId) {
        return userRepository.findById(userId).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }

    private static PendingView toView(Pending p) {
        Step step = p.purpose() == Purpose.LINK ? Step.LINK : (p.email() == null ? Step.EMAIL : Step.SIGNUP);
        return new PendingView(p.provider(), step, p.email() == null ? null : EmailMasker.mask(p.email()),
                p.expiresAt());
    }

    public enum Step { EMAIL, SIGNUP, LINK }

    public record PendingView(SocialProvider provider, Step step, String maskedEmail, LocalDateTime expiresAt) {}

    public record SignupInput(String name, String nickname, String phone, Boolean agreeTerms, Boolean agreePrivacy) {}

    public record SignedIn(Long memberId, IssuedTokens tokens) {}

    public record Connections(boolean hasPassword, List<Connection> accounts) {}

    public enum Outcome { LOGGED_IN, LINKED, PENDING, ERROR }

    public record CallbackResult(Outcome outcome, Long memberId, IssuedTokens tokens, String pendingToken,
                                 ErrorCode error) {

        static CallbackResult loggedIn(Long memberId, IssuedTokens tokens) {
            return new CallbackResult(Outcome.LOGGED_IN, memberId, tokens, null, null);
        }

        static CallbackResult linked(Long memberId) {
            return new CallbackResult(Outcome.LINKED, memberId, null, null, null);
        }

        static CallbackResult pending(String token) {
            return new CallbackResult(Outcome.PENDING, null, null, token, null);
        }

        static CallbackResult error(ErrorCode code) {
            return new CallbackResult(Outcome.ERROR, null, null, null, code);
        }
    }
}
