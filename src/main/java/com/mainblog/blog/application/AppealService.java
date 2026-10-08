package com.mainblog.blog.application;

import com.mainblog.blog.domain.AppealStatus;
import com.mainblog.blog.domain.BlacklistAppeal;
import com.mainblog.blog.domain.BlacklistAppealRepository;
import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogBlacklist;
import com.mainblog.blog.domain.BlogBlacklistRepository;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.common.config.BlogProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import com.mainblog.member.domain.User;
import com.mainblog.member.domain.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블랙리스트 해제 문의 (T070, BLG-12, FR-061~FR-063, D-34, D-98 #24, #25).
 * 대기 중 문의는 하나, 거절 뒤 retry-after(7일)가 지나야 다시 문의한다. 원문·해시 대신 일치 여부만 보여 준다.
 */
@Service
public class AppealService {

    private final BlacklistAppealRepository appealRepository;
    private final BlogBlacklistRepository blacklistRepository;
    private final BlogRepository blogRepository;
    private final UserRepository userRepository;
    private final BlacklistService blacklistService;
    private final BlacklistHasher hasher;
    private final BlogPermissionService permissionService;
    private final NotificationWriter notifications;
    private final JdbcTemplate jdbc;
    private final BlogProperties properties;
    private final Clock clock;

    public AppealService(BlacklistAppealRepository appealRepository, BlogBlacklistRepository blacklistRepository,
                         BlogRepository blogRepository, UserRepository userRepository,
                         BlacklistService blacklistService, BlacklistHasher hasher,
                         BlogPermissionService permissionService, NotificationWriter notifications,
                         JdbcTemplate jdbc, BlogProperties properties, Clock clock) {
        this.appealRepository = appealRepository;
        this.blacklistRepository = blacklistRepository;
        this.blogRepository = blogRepository;
        this.userRepository = userRepository;
        this.blacklistService = blacklistService;
        this.hasher = hasher;
        this.permissionService = permissionService;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    /** 그 블로그 블랙리스트에 걸린 로그인 회원만 남긴다(FR-061). */
    @Transactional
    public ApplicantView create(long blogId, long userId, String content) {
        Blog blog = lock(blogId);
        permissionService.context(blog, userId);
        String text = content == null || content.isBlank() ? null : content.strip();
        if (text != null && text.codePointCount(0, text.length()) > 1000) {
            throw new InvalidInputException("content", "Size");
        }
        User user = userRepository.findById(userId).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        BlogBlacklist entry = blacklistService.findMatch(blogId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_BLACKLISTED));
        LocalDateTime now = LocalDateTime.now(clock);
        List<BlacklistAppeal> mine = appealRepository.findAllByBlogAndUser(blogId, userId);
        if (mine.stream().anyMatch(BlacklistAppeal::isPending)) {
            throw new BusinessException(ErrorCode.APPEAL_PENDING_EXISTS);
        }
        mine.stream().filter(a -> a.getStatus() == AppealStatus.REJECTED && a.getProcessedAt() != null)
                .map(a -> a.getProcessedAt().plus(properties.appeal().retryAfter()))
                .filter(retryAt -> retryAt.isAfter(now))
                .findFirst()
                .ifPresent(retryAt -> {
                    throw new BusinessException(ErrorCode.APPEAL_RETRY_TOO_SOON).with("retryAt", retryAt);
                });
        boolean nameMatched = hasher.name(user.getName()).equals(entry.getNameHash());
        boolean phoneMatched = user.getPhone() != null && !user.getPhone().isBlank()
                && hasher.phone(user.getPhone()).equals(entry.getPhoneHash());
        BlacklistAppeal appeal = appealRepository.save(new BlacklistAppeal(entry.getId(), userId, text, nameMatched,
                phoneMatched));
        return new ApplicantView(appeal.getId(), appeal.getStatus().name(), now);
    }

    @Transactional(readOnly = true)
    public List<ManagerView> list(long blogId, String status) {
        AppealStatus s;
        try {
            s = status == null || status.isBlank() ? AppealStatus.PENDING : AppealStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("status", "Pattern");
        }
        return appealRepository.findAllByBlogAndStatus(blogId, s).stream().map(this::view).toList();
    }

    /** 해제하면 블랙리스트를 해제 표시하고(지우지 않음) 참여 신청할 수 있게 된다(FR-063). */
    @Transactional
    public ManagerView release(long blogId, long appealId, long actorId) {
        return process(blogId, appealId, actorId, AppealStatus.RELEASED);
    }

    @Transactional
    public ManagerView reject(long blogId, long appealId, long actorId) {
        return process(blogId, appealId, actorId, AppealStatus.REJECTED);
    }

    private ManagerView process(long blogId, long appealId, long actorId, AppealStatus result) {
        Blog blog = lock(blogId);
        permissionService.require(blog, actorId, BlogPermission.MEMBER_MANAGE);
        BlacklistAppeal appeal = appealRepository.findInBlog(blogId, appealId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!appeal.isPending()) {
            throw new BusinessException(ErrorCode.APPEAL_NOT_PENDING);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        appeal.process(result, actorId, now);
        boolean released = result == AppealStatus.RELEASED;
        if (released) {
            blacklistRepository.findById(appeal.getBlacklistId()).ifPresent(b -> b.release(now));
        }
        NotificationType type = released ? NotificationType.APPEAL_RELEASED : NotificationType.APPEAL_REJECTED;
        notifications.write(appeal.getUserId(), type, "BLOG", blogId, null,
                "'" + blog.getName() + "' 블로그 블랙리스트 해제 문의가 " + (released ? "받아들여졌습니다." : "거절되었습니다."),
                type.name() + ":" + appeal.getId());
        appealRepository.flush();
        return view(appeal);
    }

    private Blog lock(long blogId) {
        return blogRepository.findByIdForUpdate(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
    }

    private ManagerView view(BlacklistAppeal a) {
        String nickname = jdbc.query("SELECT nickname FROM users WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, a.getUserId());
        return new ManagerView(a.getId(), nickname, a.getContent(), a.isNameMatched(), a.isPhoneMatched(),
                a.getStatus().name(), a.getCreatedAt(), a.getProcessedAt());
    }

    public record ApplicantView(Long id, String status, LocalDateTime createdAt) {}

    /** 이름·전화번호 원문과 해시는 담지 않는다(FR-062). */
    public record ManagerView(Long id, String applicantNickname, String content, boolean nameMatched,
                              boolean phoneMatched, String status, LocalDateTime createdAt,
                              LocalDateTime processedAt) {}
}
