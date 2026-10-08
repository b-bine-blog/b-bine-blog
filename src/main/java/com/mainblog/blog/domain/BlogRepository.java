package com.mainblog.blog.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BlogRepository extends JpaRepository<Blog, Long> {

    Optional<Blog> findByAddress(String address);

    /** 블로그 운영 동작은 블로그 행을 잠그고 처리한다(research R-17). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Blog b where b.id = :id")
    Optional<Blog> findByIdForUpdate(Long id);
}
