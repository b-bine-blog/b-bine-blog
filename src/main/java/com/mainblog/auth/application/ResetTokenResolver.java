package com.mainblog.auth.application;

import com.mainblog.member.domain.User;
import java.util.Optional;

/** 이메일 찾기 임시 토큰으로 재설정 대상 회원을 찾는다(FR-038). 구현은 이메일 찾기(US6)에 있다. */
public interface ResetTokenResolver {

    /** 10분 안의 토큰이면 ACTIVE 회원. 만료·없음이면 비어 있다. */
    Optional<User> resolve(String rawToken);

    /** 재설정이 끝나면 토큰을 지운다(쓰이면 바로 삭제). */
    void consume(String rawToken);
}
