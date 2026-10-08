package com.mainblog.member.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 이메일은 소문자로 저장하므로 정규화한 값으로 찾는다(FR-002). */
    Optional<User> findByEmail(String email);

    /** 대소문자 무시 중복 확인(FR-010, D-97). 탈퇴 30일 안의 닉네임도 행이 남아 있어 막힌다. */
    boolean existsByNicknameIgnoreCase(String nickname);

    /** 이메일 찾기: 이름 + 전화번호(USR-08). */
    List<User> findAllByNameAndPhoneAndStatus(String name, String phone, UserStatus status);
}
