package com.mainblog.blog.application;

import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogMember;
import com.mainblog.blog.domain.BlogMemberRepository;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.blog.domain.BlogRole;
import com.mainblog.blog.domain.CloseReason;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메인 관리자가 블로그장을 강퇴한 뒤 처리 (T058, ADM-07, FR-082, D-17, D-94, research R-12). 005의 관리자 API가 부른다.
 * 부블로그장이 있으면 가장 먼저 부블로그장이 된 사람이 블로그장이 되고, 없으면 OWNER_REVOKED로 폐쇄를 예약한다.
 * 박탈된 사람은 일반 멤버로 남는다.
 */
@Service
public class OwnerSuccessionService {

    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final BlogClosingService closingService;

    public OwnerSuccessionService(BlogRepository blogRepository, BlogMemberRepository memberRepository,
                                  BlogClosingService closingService) {
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.closingService = closingService;
    }

    @Transactional
    public Result revokeOwner(long blogId, Long adminId) {
        Blog blog = blogRepository.findByIdForUpdate(blogId)
                .filter(b -> !b.isClosed())
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
        BlogMember owner = memberRepository.findByBlogIdAndRole(blogId, BlogRole.OWNER)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        List<BlogMember> managers = memberRepository.findAllByBlogIdAndRoleOrderByManagerSinceAscIdAsc(blogId,
                BlogRole.MANAGER);
        owner.makeMember();
        memberRepository.flush();
        if (!managers.isEmpty()) {
            BlogMember next = managers.get(0);
            next.makeOwner();
            memberRepository.flush();
            return new Result(next.getUserId(), null);
        }
        // 이미 블로그장이 폐쇄를 예약했으면 그 일정을 그대로 둔다. 철회할 블로그장이 없으니 그대로 폐쇄된다.
        // 박탈된 사람은 폐쇄 예정 알림에서 뺀다(005 T029, FR-024 "박탈된 사람을 제외한 멤버 전원과 구독자").
        LocalDateTime at = blog.isClosing() ? blog.getCloseScheduledAt()
                : closingService.closeBySystem(blogId, CloseReason.OWNER_REVOKED, adminId, List.of(owner.getUserId()));
        return new Result(null, at);
    }

    /** newOwnerId가 있으면 승계, 없으면 closeScheduledAt에 폐쇄 예정. revokedUserId는 박탈된 사람. */
    public record Result(Long newOwnerId, LocalDateTime closeScheduledAt) {}
}
