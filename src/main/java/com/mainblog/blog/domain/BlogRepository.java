package com.mainblog.blog.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BlogRepository extends JpaRepository<Blog, Long> {

    Optional<Blog> findByAddress(String address);

    /** 멤버 수(D-89 인기순 기준)를 원자적으로 고친다. 참여·탈퇴·강퇴와 같은 트랜잭션에서 부른다. */
    @Modifying
    @Query(value = "UPDATE blogs SET member_count = GREATEST(member_count + :delta, 0) WHERE id = :id", nativeQuery = true)
    int addMemberCount(Long id, int delta);

    /** 블로그 운영 동작은 블로그 행을 잠그고 처리한다(research R-17). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Blog b where b.id = :id")
    Optional<Blog> findByIdForUpdate(Long id);
}
