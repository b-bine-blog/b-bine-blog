package com.mainblog.member.application;

import com.mainblog.member.domain.User;
import com.mainblog.member.domain.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상단 메뉴에 필요한 최소 정보(FR-019). 역할은 블로그마다 따로 조회한다(헌장 II). */
@Service
public class SessionMemberQuery {

    private final UserRepository userRepository;
    private final ProfileImageUrls profileImageUrls;

    public SessionMemberQuery(UserRepository userRepository, ProfileImageUrls profileImageUrls) {
        this.userRepository = userRepository;
        this.profileImageUrls = profileImageUrls;
    }

    @Transactional(readOnly = true)
    public Optional<SessionMember> find(Long memberId) {
        return userRepository.findById(memberId)
                .filter(User::isActive)
                .map(u -> new SessionMember(u.getId(), u.getNickname(), profileImageUrls.of(u.getId()), u.isAdmin()));
    }

    public record SessionMember(Long memberId, String nickname, String profileImageUrl, boolean admin) {}
}
