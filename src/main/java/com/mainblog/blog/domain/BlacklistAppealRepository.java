package com.mainblog.blog.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BlacklistAppealRepository extends JpaRepository<BlacklistAppeal, Long> {

    /** 그 블로그(블랙리스트의 blog_id)에서 이 회원의 문의, 최신순. */
    @Query("select a from BlacklistAppeal a, BlogBlacklist b where a.blacklistId = b.id and b.blogId = :blogId "
            + "and a.userId = :userId order by a.id desc")
    List<BlacklistAppeal> findAllByBlogAndUser(Long blogId, Long userId);

    @Query("select a from BlacklistAppeal a, BlogBlacklist b where a.blacklistId = b.id and b.blogId = :blogId "
            + "and a.status = :status order by a.id")
    List<BlacklistAppeal> findAllByBlogAndStatus(Long blogId, AppealStatus status);

    @Query("select a from BlacklistAppeal a, BlogBlacklist b where a.blacklistId = b.id and b.blogId = :blogId "
            + "and a.id = :appealId")
    Optional<BlacklistAppeal> findInBlog(Long blogId, Long appealId);
}
