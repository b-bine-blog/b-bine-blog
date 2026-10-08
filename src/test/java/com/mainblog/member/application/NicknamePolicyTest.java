package com.mainblog.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mainblog.common.config.MemberProperties;
import com.mainblog.member.application.NicknamePolicy.Result;
import com.mainblog.member.domain.UserRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/** 닉네임 규칙 (T024, 인수 시나리오 1-7, FR-010, FR-011, D-97). */
class NicknamePolicyTest {

    private final UserRepository users = mock(UserRepository.class);
    private final NicknamePolicy policy = new NicknamePolicy(new MemberProperties(
            new MemberProperties.Withdrawal(Duration.ofDays(30)),
            new MemberProperties.ProfileImage(DataSize.ofMegabytes(3)),
            new MemberProperties.Nickname(List.of("관리자", "admin", "운영자", "탈퇴한 회원"))), users);

    @Test
    void 길이와_문자_형식() {
        assertThat(policy.checkFormat("가")).isEqualTo(Result.INVALID_FORMAT);
        assertThat(policy.checkFormat("가나다라마바사아자차카타")).isEqualTo(Result.AVAILABLE);
        assertThat(policy.checkFormat("가나다라마바사아자차카타파")).isEqualTo(Result.INVALID_FORMAT);
        assertThat(policy.checkFormat("nick!")).isEqualTo(Result.INVALID_FORMAT);
        assertThat(policy.checkFormat("nick name")).isEqualTo(Result.INVALID_FORMAT);
        assertThat(policy.checkFormat("블로거42")).isEqualTo(Result.AVAILABLE);
    }

    @Test
    void 예약어는_대소문자와_포함까지_막는다() {
        assertThat(policy.checkFormat("Admin")).isEqualTo(Result.RESERVED);
        assertThat(policy.checkFormat("admin1")).isEqualTo(Result.RESERVED);
        assertThat(policy.checkFormat("myADMIN")).isEqualTo(Result.RESERVED);
        assertThat(policy.checkFormat("관리자")).isEqualTo(Result.RESERVED);
        assertThat(policy.checkFormat("운영자님")).isEqualTo(Result.RESERVED);
    }

    @Test
    void ABC가_있으면_abc는_중복() {
        when(users.existsByNicknameIgnoreCase(anyString())).thenAnswer(inv ->
                inv.getArgument(0, String.class).equalsIgnoreCase("ABC"));
        assertThat(policy.check("abc")).isEqualTo(Result.DUPLICATE);
        assertThat(policy.check("abd")).isEqualTo(Result.AVAILABLE);
    }
}
