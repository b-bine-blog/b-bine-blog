package com.mainblog.blog.application;

import com.mainblog.blog.domain.AppealStatus;
import com.mainblog.blog.domain.BlacklistAppeal;
import com.mainblog.blog.domain.BlacklistAppealRepository;
import com.mainblog.blog.domain.BlogBlacklist;
import com.mainblog.blog.domain.BlogBlacklistRepository;
import com.mainblog.member.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블랙리스트 비교 (T064, BLG-11, FR-024, FR-058). 계정이 아니라 이메일·전화번호 해시로 비교해
 * 탈퇴 후 재가입해도 막힌다. 그 블로그의 블랙리스트만 본다.
 */
@Service
public class BlacklistService {

    private final BlogBlacklistRepository repository;
    private final BlacklistAppealRepository appealRepository;
    private final BlacklistHasher hasher;
    private final JdbcTemplate jdbc;

    public BlacklistService(BlogBlacklistRepository repository, BlacklistAppealRepository appealRepository,
                            BlacklistHasher hasher, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.appealRepository = appealRepository;
        this.hasher = hasher;
    }

    /** 이 회원과 맞는 해제 안 된 항목 중 가장 최근 것. */
    public Optional<BlogBlacklist> findMatch(long blogId, User user) {
        // 전화번호가 없는 회원끼리 빈 값 해시로 맞지 않게, 없으면 어떤 해시와도 다른 값으로 비교한다.
        String phone = user.getPhone() == null || user.getPhone().isBlank() ? "-" : hasher.phone(user.getPhone());
        List<BlogBlacklist> matches = repository.findActiveMatches(blogId, hasher.email(user.getEmail()), phone);
        return matches.stream().findFirst();
    }

    public boolean isBlacklisted(long blogId, User user) {
        return findMatch(blogId, user).isPresent();
    }

    /** 블랙리스트 열람(FR-059). 해시·원문 없이 등록일·강퇴 사유·강퇴한 사람·해제 여부만. 고치거나 지우는 기능은 없다. */
    @Transactional(readOnly = true)
    public Paged<BlacklistEntry> list(long blogId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM blog_blacklists WHERE blog_id = ?", Long.class, blogId);
        List<BlacklistEntry> items = jdbc.query("""
                SELECT b.id, b.created_at, s.reason, u.nickname, b.released, b.released_at
                FROM blog_blacklists b JOIN blog_sanctions s ON s.id = b.sanction_id
                LEFT JOIN users u ON u.id = s.handled_by
                WHERE b.blog_id = ? ORDER BY b.id DESC LIMIT ? OFFSET ?""",
                (rs, i) -> new BlacklistEntry(rs.getLong("id"), rs.getObject("created_at", LocalDateTime.class),
                        rs.getString("reason"), rs.getString("nickname"), rs.getBoolean("released"),
                        rs.getObject("released_at", LocalDateTime.class)),
                blogId, size, (page - 1) * size);
        return new Paged<>(items, page, size, total == null ? 0 : total);
    }

    public record BlacklistEntry(Long id, LocalDateTime registeredAt, String kickReason, String kickedByNickname,
                                 boolean released, LocalDateTime releasedAt) {}

    /** 대기 중인 문의가 없으면 문의를 남길 수 있다고 안내한다(BLG-12). 7일 규칙은 문의할 때 다시 본다. */
    public boolean appealAllowed(long blogId, long userId) {
        return appealRepository.findAllByBlogAndUser(blogId, userId).stream()
                .map(BlacklistAppeal::getStatus)
                .noneMatch(s -> s == AppealStatus.PENDING);
    }
}
