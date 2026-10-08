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
import com.mainblog.common.tag.TagNormalizer;
import com.mainblog.member.domain.User;
import com.mainblog.member.domain.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 블로그 만들기 (T024, BLG-01, BLG-10, FR-001~FR-006, FR-010~FR-012). */
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
    private final Clock clock;

    public BlogService(BlogRepository blogRepository, BlogMemberRepository memberRepository,
                       UserRepository userRepository, BlogAddressPolicy addressPolicy, BlogLimitPolicy limitPolicy,
                       BlogTagStore tagStore, ShareLinkService shareLinkService,
                       BlogPermissionService permissionService, BlogViewService viewService,
                       BlogProperties properties, Clock clock) {
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
        LocalDateTime now = LocalDateTime.now(clock);
        BlogMember owner = memberRepository.save(BlogMember.owner(blog.getId(), userId, now));
        tagStore.replace(blog.getId(), tags);
        if (cmd.visibility() == Visibility.LINK) {
            shareLinkService.issue(blog.getId());
        }
        return viewService.detail(new BlogContext(blog, owner, userId));
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
