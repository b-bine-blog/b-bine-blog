package com.mainblog.member.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileImageRepository extends JpaRepository<ProfileImage, Long> {

    /** 지금 쓰는 사진(지우지 않은 것). 보통 1건이다. */
    List<ProfileImage> findAllByOwnerIdAndDeletedAtIsNull(Long ownerId);
}
