package com.mainblog.blog.application;

import com.mainblog.blog.domain.AppealStatus;
import com.mainblog.blog.domain.BlacklistAppeal;
import com.mainblog.blog.domain.BlacklistAppealRepository;
import com.mainblog.blog.domain.BlogBlacklist;
import com.mainblog.blog.domain.BlogBlacklistRepository;
import com.mainblog.member.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 블랙리스트 비교 (T064, BLG-11, FR-024, FR-058). 계정이 아니라 이메일·전화번호 해시로 비교해
 * 탈퇴 후 재가입해도 막힌다. 그 블로그의 블랙리스트만 본다.
 */
@Service
public class BlacklistService {

    private final BlogBlacklistRepository repository;
    private final BlacklistAppealRepository appealRepository;
    private final BlacklistHasher hasher;

    public BlacklistService(BlogBlacklistRepository repository, BlacklistAppealRepository appealRepository,
                            BlacklistHasher hasher) {
        this.repository = repository;
        this.appealRepository = appealRepository;
        this.hasher = hasher;
    }

    /** 이 회원과 맞는 해제 안 된 항목 중 가장 최근 것. */
    public Optional<BlogBlacklist> findMatch(long blogId, User user) {
        List<BlogBlacklist> matches = repository.findActiveMatches(blogId, hasher.email(user.getEmail()),
                hasher.phone(user.getPhone()));
        return matches.stream().findFirst();
    }

    public boolean isBlacklisted(long blogId, User user) {
        return findMatch(blogId, user).isPresent();
    }

    /** 대기 중인 문의가 없으면 문의를 남길 수 있다고 안내한다(BLG-12). 7일 규칙은 문의할 때 다시 본다. */
    public boolean appealAllowed(long blogId, long userId) {
        return appealRepository.findAllByBlogAndUser(blogId, userId).stream()
                .map(BlacklistAppeal::getStatus)
                .noneMatch(s -> s == AppealStatus.PENDING);
    }
}
