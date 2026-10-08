package com.mainblog.social.application;

import com.mainblog.blog.application.JoinService;
import com.mainblog.board.application.BoardPaging;
import com.mainblog.common.config.SocialProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.member.application.ProfileImageUrls;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 차단·해제·내 차단 목록 (003 T042, SOC-05, FR-016, FR-019, FR-020, D-90, D-102 #10~#13, research R-13).
 * 차단하면 같은 트랜잭션에서 두 방향 팔로우를 지우고, 내가 블로그장인 블로그의 대기 중 신청을 자동 거절한다.
 * 숨기기는 읽는 쪽(피드·검색·태그·블로그 글 목록·댓글)이 user_blocks로 한다. 차단당한 회원에게는 알리지 않고,
 * 해제해도 끊긴 팔로우는 되살리지 않는다.
 */
@Service
public class BlockService {

    private final JdbcTemplate jdbc;
    private final SocialGuard guard;
    private final JoinService joinService;
    private final ProfileImageUrls profileImageUrls;
    private final BoardPaging paging;
    private final SocialProperties props;
    private final Clock clock;

    public BlockService(JdbcTemplate jdbc, SocialGuard guard, JoinService joinService,
                        ProfileImageUrls profileImageUrls, BoardPaging paging, SocialProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.joinService = joinService;
        this.profileImageUrls = profileImageUrls;
        this.paging = paging;
        this.props = props;
        this.clock = clock;
    }

    public record BlockedUser(long id, String nickname, String profileImageUrl, LocalDateTime blockedAt) {}

    public record BlockPage(List<BlockedUser> items, int page, int size, long totalElements, int totalPages) {}

    /** 이미 차단했어도 성공(멱등). */
    @Transactional
    public void block(long blockerId, long targetId) {
        guard.requireActor(blockerId);
        guard.requireTarget(targetId);
        if (blockerId == targetId) {
            throw new BusinessException(ErrorCode.SELF_NOT_ALLOWED);
        }
        int inserted = jdbc.update("INSERT IGNORE INTO user_blocks (blocker_id, blocked_id, created_at) VALUES (?, ?, ?)",
                blockerId, targetId, LocalDateTime.now(clock));
        jdbc.update("DELETE FROM follows WHERE (follower_id = ? AND followee_id = ?) "
                + "OR (follower_id = ? AND followee_id = ?)", blockerId, targetId, targetId, blockerId);
        if (inserted > 0) {
            joinService.rejectPendingBlockedByOwner(blockerId, targetId);
        }
    }

    /** 차단하지 않았어도 성공. 관리자·탈퇴 대상도 해제는 막지 않는다. */
    @Transactional
    public void unblock(long blockerId, long targetId) {
        jdbc.update("DELETE FROM user_blocks WHERE blocker_id = ? AND blocked_id = ?", blockerId, targetId);
    }

    /** 최근 차단 순. 탈퇴한 회원은 목록에서 뺀다(탈퇴하면 관계도 지워진다, R-16). */
    @Transactional(readOnly = true)
    public BlockPage blocks(long blockerId, Integer page) {
        int p = paging.page(page);
        int size = props.list().pageSize();
        String from = " FROM user_blocks b JOIN users u ON u.id = b.blocked_id AND u.status <> 'WITHDRAWN' "
                + "WHERE b.blocker_id = ?";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, blockerId);
        long totalElements = total == null ? 0 : total;
        List<BlockedUser> items = jdbc.query("SELECT u.id, u.nickname, b.created_at" + from
                        + " ORDER BY b.created_at DESC, b.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new BlockedUser(rs.getLong(1), rs.getString(2), profileImageUrls.of(rs.getLong(1)),
                        rs.getTimestamp(3).toLocalDateTime()),
                blockerId, size, (long) (p - 1) * size);
        return new BlockPage(items, p, size, totalElements, (int) ((totalElements + size - 1) / size));
    }
}
