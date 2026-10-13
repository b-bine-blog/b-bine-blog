package com.mainblog.member.application;

import com.mainblog.auth.application.TokenService;
import com.mainblog.auth.application.TokenService.IssuedTokens;
import com.mainblog.common.config.MemberProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.storage.FileStorage;
import com.mainblog.common.storage.ImageSanitizer;
import com.mainblog.common.storage.ImageType;
import com.mainblog.common.storage.ImageValidator;
import com.mainblog.member.domain.ProfileImage;
import com.mainblog.member.domain.ProfileImageRepository;
import com.mainblog.member.domain.User;
import com.mainblog.member.domain.UserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 내 정보 보기와 회원정보 수정 (T062, USR-07, FR-040~FR-044, D-97).
 * 모든 메서드는 로그인한 회원 번호만 받는다. 이메일과 이름은 바꾸는 길이 없다(FR-041).
 */
@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private final UserRepository userRepository;
    private final ProfileImageRepository profileImageRepository;
    private final NicknamePolicy nicknamePolicy;
    private final PasswordPolicy passwordPolicy;
    private final MemberInputNormalizer normalizer;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final ImageValidator imageValidator;
    private final ImageSanitizer imageSanitizer;
    private final FileStorage fileStorage;
    private final ProfileImageUrls profileImageUrls;
    private final MemberProperties properties;
    private final Clock clock;

    public MemberService(UserRepository userRepository, ProfileImageRepository profileImageRepository,
                         NicknamePolicy nicknamePolicy, PasswordPolicy passwordPolicy,
                         MemberInputNormalizer normalizer, PasswordEncoder passwordEncoder, TokenService tokenService,
                         ImageValidator imageValidator, ImageSanitizer imageSanitizer, FileStorage fileStorage,
                         ProfileImageUrls profileImageUrls, MemberProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.profileImageRepository = profileImageRepository;
        this.nicknamePolicy = nicknamePolicy;
        this.passwordPolicy = passwordPolicy;
        this.normalizer = normalizer;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.imageValidator = imageValidator;
        this.imageSanitizer = imageSanitizer;
        this.fileStorage = fileStorage;
        this.profileImageUrls = profileImageUrls;
        this.properties = properties;
        this.clock = clock;
    }

    /** 본인에게는 가리지 않고 그대로 보여준다(FR-040, 4.4). 비밀번호는 없다. */
    @Transactional(readOnly = true)
    public MyInfo myInfo(Long memberId) {
        return toMyInfo(activeMember(memberId));
    }

    /** 닉네임·전화번호 수정(FR-041, FR-042). null이면 그 항목은 그대로 둔다. */
    @Transactional
    public MyInfo update(Long memberId, String rawNickname, String rawPhone) {
        User user = activeMember(memberId);
        if (rawNickname != null) {
            String nickname = rawNickname.strip();
            if (!nickname.equals(user.getNickname())) {
                switch (nicknamePolicy.checkForMember(nickname, memberId)) {
                    case INVALID_FORMAT -> throw new InvalidInputException("nickname", "Pattern");
                    case RESERVED -> throw new InvalidInputException("nickname", "Reserved");
                    case DUPLICATE -> throw new BusinessException(ErrorCode.NICKNAME_DUPLICATE);
                    case AVAILABLE -> user.changeNickname(nickname);
                }
            }
        }
        if (rawPhone != null) {
            user.changePhone(normalizer.phone(rawPhone));
        }
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 닉네임으로 바꾼 경우 DB UNIQUE가 막는다
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATE);
        }
        return toMyInfo(user);
    }

    /**
     * 비밀번호 변경(FR-044, D-97). 현재 비밀번호 확인 → 규칙·두 값 일치 → 저장 →
     * 다른 기기 RT 모두 폐기, 이 기기에는 새 AT·RT를 준다(호출한 쪽이 쿠키로 내려준다).
     * 소셜로만 가입해 비밀번호가 없으면 현재 비밀번호 확인 없이 새로 만든다(008 FR-009, D-125).
     */
    @Transactional
    public IssuedTokens changePassword(Long memberId, String currentPassword, String newPassword,
                                       String newPasswordConfirm, String rawRefreshToken) {
        User user = activeMember(memberId);
        if (user.getPasswordHash() != null
                && (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash()))) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        if (!passwordPolicy.isValid(newPassword)) {
            throw new InvalidInputException("newPassword", "Pattern");
        }
        if (!newPassword.equals(newPasswordConfirm)) {
            throw new InvalidInputException("newPasswordConfirm", "Mismatch");
        }
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.saveAndFlush(user);
        return tokenService.reissueAfterPasswordChange(memberId, rawRefreshToken);
    }

    /**
     * 프로필 사진 교체(FR-043). 확장자·MIME·시그니처 확인 → 메타데이터 제거 → UUID 이름으로 저장 →
     * 이전 사진은 deleted_at만 두고 파일은 30일 뒤 04:00 배치가 지운다.
     */
    @Transactional
    public String changeProfileImage(Long memberId, byte[] content, String originalFilename, String contentType) {
        activeMember(memberId);
        ImageType type = imageValidator.validate(content, originalFilename, contentType,
                properties.profileImage().maxSize().toBytes());
        byte[] clean = imageSanitizer.sanitize(content, type, properties.profileImage().maxPixels());
        String storedName = store(clean, type);
        deleteFileIfRolledBack(storedName);

        LocalDateTime now = LocalDateTime.now(clock);
        profileImageRepository.findAllByOwnerIdAndDeletedAtIsNull(memberId).forEach(old -> old.delete(now));
        profileImageRepository.save(new ProfileImage(memberId, storedName, originalFilename, type.mimeType(),
                clean.length));
        return fileStorage.url(storedName);
    }

    /** 프로필 사진 지우기. 없어도 그대로 끝낸다. */
    @Transactional
    public void deleteProfileImage(Long memberId) {
        activeMember(memberId);
        LocalDateTime now = LocalDateTime.now(clock);
        profileImageRepository.findAllByOwnerIdAndDeletedAtIsNull(memberId).forEach(old -> old.delete(now));
    }

    private String store(byte[] content, ImageType type) {
        try {
            return fileStorage.save(content, type.extension());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** DB 저장이 되돌려지면 이미 쓴 파일을 지운다. */
    private void deleteFileIfRolledBack(String storedName) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        fileStorage.delete(storedName);
                    } catch (IOException e) {
                        log.warn("되돌린 프로필 사진 파일 삭제 실패: {}", e.getClass().getSimpleName());
                    }
                }
            }
        });
    }

    private User activeMember(Long memberId) {
        return userRepository.findById(memberId).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }

    private MyInfo toMyInfo(User user) {
        LocalDate joinedOn = user.getCreatedAt() == null ? null : user.getCreatedAt().toLocalDate();
        return new MyInfo(user.getEmail(), user.getName(), user.getNickname(), user.getPhone(),
                profileImageUrls.of(user.getId()), user.getBio(), joinedOn);
    }

    /** contracts의 MyInfo. 비밀번호 필드 없음. */
    public record MyInfo(String email, String name, String nickname, String phone, String profileImageUrl,
                         String bio, LocalDate joinedOn) {}
}
