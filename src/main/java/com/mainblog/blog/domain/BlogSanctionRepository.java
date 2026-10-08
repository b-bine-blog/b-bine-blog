package com.mainblog.blog.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogSanctionRepository extends JpaRepository<BlogSanction, Long> {

    List<BlogSanction> findAllByBlogIdAndUserIdOrderByIdDesc(Long blogId, Long userId);

    Optional<BlogSanction> findFirstByBlogIdAndUserIdAndSanctionTypeInOrderByIdDesc(
            Long blogId, Long userId, List<SanctionType> types);

    long countByBlogIdAndUserIdAndSanctionTypeAndStartsAtAfter(Long blogId, Long userId, SanctionType type,
                                                              LocalDateTime after);
}
