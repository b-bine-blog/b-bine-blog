package com.mainblog.blog.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogJoinRequestRepository extends JpaRepository<BlogJoinRequest, Long> {

    Optional<BlogJoinRequest> findFirstByBlogIdAndUserIdAndStatus(Long blogId, Long userId, JoinRequestStatus status);

    /** 7일 재신청 기준이 되는 마지막 거절(FR-019). */
    Optional<BlogJoinRequest> findFirstByBlogIdAndUserIdAndStatusInOrderByProcessedAtDesc(
            Long blogId, Long userId, List<JoinRequestStatus> statuses);

    Page<BlogJoinRequest> findAllByBlogIdAndStatusOrderByRequestedAtAscIdAsc(Long blogId, JoinRequestStatus status,
                                                                            Pageable pageable);

    List<BlogJoinRequest> findAllByBlogIdAndStatus(Long blogId, JoinRequestStatus status);
}
