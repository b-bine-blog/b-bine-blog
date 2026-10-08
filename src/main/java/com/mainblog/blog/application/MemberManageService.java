package com.mainblog.blog.application;

import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogMember;
import com.mainblog.blog.domain.BlogMemberRepository;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 블로그 탈퇴와 멤버 빼기 (T057, BLG-07, FR-064, FR-065, D-33). 멤버 관리 화면 기능은 US8에서 더한다. */
@Service
public class MemberManageService {

    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final BlogPermissionService permissionService;
    private final DelegationService delegationService;
    private final JdbcTemplate jdbc;

    public MemberManageService(BlogRepository blogRepository, BlogMemberRepository memberRepository,
                               BlogPermissionService permissionService, DelegationService delegationService,
                               JdbcTemplate jdbc) {
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.permissionService = permissionService;
        this.delegationService = delegationService;
        this.jdbc = jdbc;
    }

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
