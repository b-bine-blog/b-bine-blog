package com.mainblog.blog.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogDelegationRepository extends JpaRepository<BlogDelegation, Long> {

    Optional<BlogDelegation> findFirstByBlogIdAndStatus(Long blogId, DelegationStatus status);

    List<BlogDelegation> findAllByStatusAndExpiresAtLessThanEqual(DelegationStatus status, LocalDateTime now);
}
