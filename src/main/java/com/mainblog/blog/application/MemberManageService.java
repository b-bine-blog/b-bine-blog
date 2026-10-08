package com.mainblog.blog.application;

import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogMember;
import com.mainblog.blog.domain.BlogMemberRepository;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.config.BlogProperties;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.masking.EmailMasker;
import com.mainblog.common.masking.PhoneMasker;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 멤버 정보·부블로그장 지정·블로그 탈퇴·멤버 빼기 (T057, T067, BLG-07, FR-039~FR-047, FR-064, FR-065, D-33). */
@Service
public class MemberManageService {

    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final BlogPermissionService permissionService;
    private final DelegationService delegationService;
    private final JdbcTemplate jdbc;
    private final SuspensionQuery suspensionQuery;
    private final BlogProperties properties;
    private final Clock clock;

    public MemberManageService(BlogRepository blogRepository, BlogMemberRepository memberRepository,
                               BlogPermissionService permissionService, DelegationService delegationService,
                               JdbcTemplate jdbc, SuspensionQuery suspensionQuery, BlogProperties properties,
                               Clock clock) {
        this.suspensionQuery = suspensionQuery;
        this.properties = properties;
        this.clock = clock;
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.permissionService = permissionService;
        this.delegationService = delegationService;
        this.jdbc = jdbc;
    }

    /**
     * 멤버 정보 목록, 멤버 관리 권한 (T067, FR-044~FR-047, FR-053). 이메일·전화번호는 서버가 가린 값만 넣는다(D-22).
     * 블로그장 → 부블로그장(먼저 된 순) → 멤버(들어온 순).
     */
    @Transactional(readOnly = true)
    public Paged<MemberView> listMembers(long blogId, int page, int size) {
        permissionService.require(blogId, BlogPermission.MEMBER_MANAGE);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM blog_members WHERE blog_id = ?", Long.class, blogId);
        List<Long> userIds = jdbc.queryForList("""
                SELECT user_id FROM blog_members WHERE blog_id = ?
                ORDER BY FIELD(role, 'OWNER', 'MANAGER', 'MEMBER'), manager_since, joined_at, id
                LIMIT ? OFFSET ?""", Long.class, blogId, size, (page - 1) * size);
        List<MemberView> items = userIds.stream().map(id -> memberView(blogId, id)).toList();
        return new Paged<>(items, page, size, total == null ? 0 : total);
    }

    /** 블로그장만 지정하고 권한을 정한다(FR-039~FR-043). 처음 지정할 때만 manager_since를 남긴다. */
    @Transactional
    public MemberView setManager(long blogId, long actorId, long userId, boolean blogInfo, boolean memberManage,
                                 boolean postManage) {
        Blog blog = lockForOwner(blogId, actorId);
        BlogMember target = memberRepository.findByBlogIdAndUserId(blog.getId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (target.isOwner()) {
            throw new BusinessException(ErrorCode.BLOG_FORBIDDEN);
        }
        target.makeManager(blogInfo, memberManage, postManage, LocalDateTime.now(clock));
        memberRepository.flush();
        return memberView(blogId, userId);
    }

    /** 해제하면 다음 요청부터 일반 멤버 권한만 갖는다(US8 시나리오 6). */
    @Transactional
    public void removeManager(long blogId, long actorId, long userId) {
        Blog blog = lockForOwner(blogId, actorId);
        BlogMember target = memberRepository.findByBlogIdAndUserId(blog.getId(), userId)
                .filter(BlogMember::isManager)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        target.makeMember();
        memberRepository.flush();
    }

    private Blog lockForOwner(long blogId, long actorId) {
        Blog blog = blogRepository.findByIdForUpdate(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
        permissionService.require(blog, actorId, BlogPermission.OWNER);
        return blog;
    }

    private MemberView memberView(long blogId, long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<SuspensionQuery.ActiveSuspension> suspension = suspensionQuery.find(blogId, userId, now);
        int count = suspensionQuery.countInWindow(blogId, userId, now.minus(properties.sanction().countWindow()));
        return jdbc.queryForObject("""
                SELECT m.user_id, u.nickname, u.name, u.email, u.phone, m.role, m.joined_at, m.manager_since,
                       m.perm_blog_info, m.perm_member_manage, m.perm_post_manage
                FROM blog_members m JOIN users u ON u.id = m.user_id WHERE m.blog_id = ? AND m.user_id = ?""",
                (rs, i) -> new MemberView(rs.getLong("user_id"), rs.getString("nickname"), rs.getString("name"),
                        EmailMasker.mask(rs.getString("email")), PhoneMasker.mask(rs.getString("phone")),
                        rs.getString("role"), rs.getObject("joined_at", LocalDateTime.class),
                        rs.getObject("manager_since", LocalDateTime.class),
                        new Permissions(rs.getBoolean("perm_blog_info"), rs.getBoolean("perm_member_manage"),
                                rs.getBoolean("perm_post_manage")),
                        suspension.map(SuspensionQuery.ActiveSuspension::endsAt).orElse(null), suspension.isPresent(),
                        count, count >= properties.sanction().kickSuggestCount()),
                blogId, userId);
    }

    public record Permissions(boolean blogInfo, boolean memberManage, boolean postManage) {}

    /** 원래 이메일·전화번호는 담지 않는다(FR-047). */
    public record MemberView(Long userId, String nickname, String name, String maskedEmail, String maskedPhone,
                             String role, LocalDateTime joinedAt, LocalDateTime managerSince, Permissions permissions,
                             LocalDateTime suspendedUntil, boolean suspended, int suspendCountInWindow,
                             boolean kickSuggested) {}

    /** 스스로 나간다. 블로그장은 위임하거나 폐쇄해야 하므로 409(FR-065). */
    @Transactional
    public void leave(long blogId, long userId) {
        Blog blog = blogRepository.findByIdForUpdate(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
        BlogContext ctx = permissionService.require(blog, userId, BlogPermission.MEMBER);
        if (ctx.member().isOwner()) {
            throw new BusinessException(ErrorCode.OWNER_CANNOT_LEAVE);
        }
        removeMember(blog, ctx.member());
    }

    /**
     * 멤버 행을 지우고, 그 블로그에 쓴 글을 "탈퇴한 계정"으로 표시하고, 멤버 수를 줄이고, 받은 위임 요청을 취소한다.
     * 블로그 탈퇴와 강퇴(US7)가 블로그 행을 잠근 트랜잭션에서 부른다.
     */
    void removeMember(Blog blog, BlogMember member) {
        memberRepository.delete(member);
        memberRepository.flush();
        jdbc.update("UPDATE posts SET author_left = TRUE WHERE blog_id = ? AND author_id = ?", blog.getId(),
                member.getUserId());
        blogRepository.addMemberCount(blog.getId(), -1);
        delegationService.cancelPendingFor(blog, member.getUserId());
    }
}
