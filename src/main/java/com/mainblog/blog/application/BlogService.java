package com.mainblog.blog.application;

import com.mainblog.blog.application.BlogViews.BlogDetail;
import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogMember;
import com.mainblog.blog.domain.BlogMemberRepository;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.blog.domain.JoinPolicy;
import com.mainblog.blog.domain.Visibility;
import com.mainblog.common.config.BlogProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.common.notification.NotificationType;
import com.mainblog.common.notification.NotificationWriter;
import com.mainblog.common.tag.TagNormalizer;
import com.mainblog.member.domain.User;
import com.mainblog.member.domain.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 블로그 만들기·정보 수정 (T024, T044, BLG-01, BLG-10, FR-001~FR-013, FR-031, FR-032). */
@Service
public class BlogService {

    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final BlogAddressPolicy addressPolicy;
    private final BlogLimitPolicy limitPolicy;
    private final BlogTagStore tagStore;
    private final ShareLinkService shareLinkService;
    private final BlogPermissionService permissionService;
    private final BlogViewService viewService;
    private final BlogProperties properties;
    private final SubscriptionService subscriptionService;
    private final NotificationWriter notificationWriter;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public BlogService(BlogRepository blogRepository, BlogMemberRepository memberRepository,
                       UserRepository userRepository, BlogAddressPolicy addressPolicy, BlogLimitPolicy limitPolicy,
                       BlogTagStore tagStore, ShareLinkService shareLinkService,
                       BlogPermissionService permissionService, BlogViewService viewService,
                       BlogProperties properties, SubscriptionService subscriptionService,
                       NotificationWriter notificationWriter, Clock clock) {
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.addressPolicy = addressPolicy;
        this.limitPolicy = limitPolicy;
        this.tagStore = tagStore;
        this.shareLinkService = shareLinkService;
        this.permissionService = permissionService;
        this.viewService = viewService;
        this.properties = properties;
        this.subscriptionService = subscriptionService;
        this.notificationWriter = notificationWriter;
        this.clock = clock;
    }

    public record CreateCommand(String name, String address, String description, List<String> tags,
                                Visibility visibility, JoinPolicy joinPolicy) {}

    /**
     * 만든 회원이 블로그장이 된다(FR-002). 회원 행을 잠가 개수 확인과 생성을 한 번에 처리한다(FR-012).
     * 주소 중복(폐쇄 30일 안 포함)은 DB 유니크 제약이 막고 409로 바꾼다(FR-003, FR-008).
     */
    @Transactional
    public BlogDetail create(long userId, CreateCommand cmd) {
        // 잠금을 트랜잭션의 첫 읽기로 둔다. MySQL REPEATABLE READ는 첫 일반 읽기 때 스냅샷을 잡으므로,
        // 다른 읽기를 먼저 하면 잠금을 기다린 뒤에도 예전 개수를 보게 된다.
        limitPolicy.lockUser(userId);
        User user = activeUser(userId);
        if (user.isAdmin()) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_ALLOWED);
        }
        String name = checkName(cmd.name());
        String address = addressPolicy.check(cmd.address());
        String description = checkDescription(cmd.description());
        List<String> tags = normalizeTags(cmd.tags());
        if (cmd.visibility() == null) {
            throw new InvalidInputException("visibility", "NotNull");
        }
        if (cmd.joinPolicy() == null) {
            throw new InvalidInputException("joinPolicy", "NotNull");
        }

        limitPolicy.checkCanHave(userId, cmd.visibility(), null);

        Blog blog;
        try {
            blog = blogRepository.saveAndFlush(Blog.create(name, address, description, cmd.visibility(),
                    cmd.joinPolicy()));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.BLOG_ADDRESS_TAKEN);
        }
        entityManager.refresh(blog); // DB가 채운 생성 일시를 응답에 싣는다
        LocalDateTime now = LocalDateTime.now(clock);
        BlogMember owner = memberRepository.save(BlogMember.owner(blog.getId(), userId, now));
        tagStore.replace(blog.getId(), tags);
        if (cmd.visibility() == Visibility.LINK) {
            shareLinkService.issue(blog.getId());
        }
        return viewService.detail(new BlogContext(blog, owner, userId));
    }

    /**
     * 정보 수정 (T044, FR-009, FR-013, FR-031, FR-032). 보낸 필드만 바꾼다. 주소는 바꿀 수 없다.
     * 이름·소개·태그는 BLOG_INFO, 공개 범위·참여 방식은 OWNER 권한이다. 하나라도 권한이 없으면 아무것도 바꾸지 않는다.
     */
    @Transactional
    public BlogDetail update(long blogId, long userId, UpdateCommand cmd) {
        // 잠금 순서 users → blogs(R-17). 회원 행 잠금을 첫 읽기로 둬 개수 확인이 최신 값을 보게 한다.
        limitPolicy.lockUser(userId);
        Blog blog = blogRepository.findByIdForUpdate(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
        BlogContext ctx = permissionService.context(blog, userId);
        if (cmd.touchesInfo()) {
            permissionService.require(blog, userId, BlogPermission.BLOG_INFO);
        }
        if (cmd.touchesOwnerSettings()) {
            permissionService.require(blog, userId, BlogPermission.OWNER);
        }
        if (cmd.name() != null) {
            blog.changeName(checkName(cmd.name()));
        }
        if (cmd.descriptionSet()) {
            blog.changeDescription(checkDescription(cmd.description()));
        }
        List<String> tags = cmd.tags() == null ? null : normalizeTags(cmd.tags());
        if (cmd.joinPolicy() != null) {
            blog.changeJoinPolicy(cmd.joinPolicy());
        }
        Visibility before = blog.getVisibility();
        Visibility after = cmd.visibility();
        if (after != null && after != before) {
            if ((before == Visibility.PRIVATE) != (after == Visibility.PRIVATE)) {
                limitPolicy.checkCanHave(userId, after, blogId);
            }
            blog.changeVisibility(after);
            if (before == Visibility.LINK) {
                shareLinkService.deactivate(blogId);
            }
            if (after == Visibility.LINK) {
                shareLinkService.issue(blogId);
            }
            if (after == Visibility.PRIVATE) {
                notifyPrivate(blog, userId);
            }
        }
        if (tags != null) {
            tagStore.replace(blogId, tags);
        }
        blogRepository.flush();
        return viewService.detail(ctx);
    }

    /** 구독은 그대로 두고 구독자 모두에게 끌 수 없는 알림을 보낸다(FR-031). */
    private void notifyPrivate(Blog blog, long actorId) {
        String stamp = String.valueOf(LocalDateTime.now(clock).atZone(clock.getZone()).toInstant().toEpochMilli());
        for (Long subscriber : subscriptionService.subscriberIds(blog.getId())) {
            notificationWriter.write(subscriber, NotificationType.BLOG_PRIVATE, "BLOG", blog.getId(), actorId,
                    "'" + blog.getName() + "' 블로그가 비공개로 바뀌었습니다.",
                    "BLOG_PRIVATE:" + blog.getId() + ":" + stamp);
        }
    }

    /** 보내지 않은 필드는 null. description은 null로 지울 수 있어 descriptionSet으로 보냈는지 따로 본다. */
    public record UpdateCommand(String name, boolean descriptionSet, String description, List<String> tags,
                                Visibility visibility, JoinPolicy joinPolicy) {

        boolean touchesInfo() {
            return name != null || descriptionSet || tags != null;
        }

        boolean touchesOwnerSettings() {
            return visibility != null || joinPolicy != null;
        }
    }

    String checkName(String raw) {
        String name = raw == null ? "" : raw.strip();
        int len = name.codePointCount(0, name.length());
        if (len < 1 || len > properties.name().maxLength()) {
            throw new InvalidInputException("name", "Size");
        }
        return name;
    }

    String checkDescription(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String d = raw.strip();
        if (d.codePointCount(0, d.length()) > properties.description().maxLength()) {
            throw new InvalidInputException("description", "Size");
        }
        return d;
    }

    List<String> normalizeTags(List<String> raw) {
        return TagNormalizer.normalizeAll(raw, properties.tag().maxCount(), properties.tag().maxLength());
    }

    User activeUser(long userId) {
        return userRepository.findById(userId).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }

    BlogPermissionService permissionService() {
        return permissionService;
    }
}
