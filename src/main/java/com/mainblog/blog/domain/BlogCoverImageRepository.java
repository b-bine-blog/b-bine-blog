package com.mainblog.blog.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogCoverImageRepository extends JpaRepository<BlogCoverImage, Long> {

    List<BlogCoverImage> findAllByOwnerTypeAndOwnerIdAndDeletedAtIsNull(String ownerType, Long blogId);
}
