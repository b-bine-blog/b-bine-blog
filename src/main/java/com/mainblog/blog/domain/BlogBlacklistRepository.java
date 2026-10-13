package com.mainblog.blog.domain;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BlogBlacklistRepository extends JpaRepository<BlogBlacklist, Long> {

    /** 이메일 또는 전화번호가 맞는 해제 안 된 항목, 최신순(FR-024, FR-058). */
    @Query("select b from BlogBlacklist b where b.blogId = :blogId and b.released = false "
            + "and (b.emailHash = :emailHash or b.phoneHash = :phoneHash) order by b.id desc")
    List<BlogBlacklist> findActiveMatches(Long blogId, String emailHash, String phoneHash);

    Page<BlogBlacklist> findAllByBlogIdOrderByIdDesc(Long blogId, Pageable pageable);
}
