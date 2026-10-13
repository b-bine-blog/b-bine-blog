package com.mainblog.blog.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogShareLinkRepository extends JpaRepository<BlogShareLink, Long> {

    Optional<BlogShareLink> findFirstByBlogIdAndActiveTrue(Long blogId);

    Optional<BlogShareLink> findByTokenAndActiveTrue(String token);
}
