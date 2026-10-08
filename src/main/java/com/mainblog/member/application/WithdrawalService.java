package com.mainblog.member.application;

import com.mainblog.auth.domain.RefreshTokenRepository;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.member.domain.OwnedBlogQuery;
import com.mainblog.member.domain.OwnedBlogQuery.OwnedBlog;
import com.mainblog.member.domain.User;
import com.mainblog.member.domain.UserRepository;
import com.mainblog.social.application.SocialCleanup;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원탈퇴 (T048, USR-05, FR-045~FR-048, D-15, D-18, D-97).
 * 블로그장인 블로그가 있으면(폐쇄 예정 포함) 막는다. 탈퇴하면 이메일을 30일 보관 칸으로 옮겨 같은 이메일로 바로 재가입할 수 있고,
 * 닉네임은 30일 동안 그대로 둬 다른 사람이 쓰지 못한다. 팔로우·구독·차단 관계는 같은 트랜잭션에서 지운다(003 R-16).
 */
@Service
public class WithdrawalService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OwnedBlogQuery ownedBlogQuery;
    private final PasswordEncoder passwordEncoder;
    private final SocialCleanup socialCleanup;
    private final Clock clock;

    public WithdrawalService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                             OwnedBlogQuery ownedBlogQuery, PasswordEncoder passwordEncoder,
                             SocialCleanup socialCleanup, Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.ownedBlogQuery = ownedBlogQuery;
        this.passwordEncoder = passwordEncoder;
        this.socialCleanup = socialCleanup;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<OwnedBlog> ownedBlogs(Long userId) {
        return ownedBlogQuery.findByOwner(userId);
    }

    @Transactional
    public void withdraw(Long userId, String password) {
        User user = userRepository.findById(userId).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        if (!ownedBlogQuery.findByOwner(userId).isEmpty()) {
            throw new BusinessException(ErrorCode.OWNS_BLOGS);
        }
        user.withdraw(LocalDateTime.now(clock));
        refreshTokenRepository.revokeAllByUserId(userId);
        socialCleanup.onWithdraw(userId); // 팔로우·구독·차단은 바로 지우고 신고는 남긴다(003 T045, D-102 #16)
    }
}
