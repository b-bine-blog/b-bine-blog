package com.mainblog.member.domain;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 이메일은 소문자로 저장하므로 정규화한 값으로 찾는다(FR-002). */
    Optional<User> findByEmail(String email);

    /** 대소문자 무시 중복 확인(FR-010, D-97). 탈퇴 30일 안의 닉네임도 행이 남아 있어 막힌다. */
    boolean existsByNicknameIgnoreCase(String nickname);

    /** 닉네임 변경: 자기 자신은 빼고 대소문자 무시 중복 확인(FR-042). */
    boolean existsByNicknameIgnoreCaseAndIdNot(String nickname, Long id);

    /** 소셜 연결 끊기처럼 로그인 수단을 세는 동안 회원 행을 잠근다(008 FR-010, SC-006). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(Long id);

    /** 이메일 찾기: 이름 + 전화번호(USR-08). */
    List<User> findAllByNameAndPhoneAndStatus(String name, String phone, UserStatus status);
}
