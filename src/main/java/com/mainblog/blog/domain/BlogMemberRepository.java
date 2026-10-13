package com.mainblog.blog.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogMemberRepository extends JpaRepository<BlogMember, Long> {

    Optional<BlogMember> findByBlogIdAndUserId(Long blogId, Long userId);

    Optional<BlogMember> findByBlogIdAndRole(Long blogId, BlogRole role);

    List<BlogMember> findAllByBlogId(Long blogId);

    long countByBlogId(Long blogId);

    /** 권한 박탈 때 자동 승계 순서: 가장 먼저 부블로그장이 된 사람, 같으면 번호 순(FR-082). */
    List<BlogMember> findAllByBlogIdAndRoleOrderByManagerSinceAscIdAsc(Long blogId, BlogRole role);
}
