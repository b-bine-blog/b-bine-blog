package com.mainblog.social.application;

import com.mainblog.board.application.BoardPaging;
import com.mainblog.common.config.SocialProperties;
import com.mainblog.member.application.ProfileImageUrls;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팔로워·팔로잉 목록 (003 T039, SOC-02, FR-015, D-22, D-102 #9). 프로필을 볼 수 있는 누구나 본다(비회원 포함).
 * 최근에 팔로우한 순서로 설정값(20)씩 번호 페이지. 항목은 번호·닉네임·프로필 사진만 담는다(이메일·이름·전화번호 없음).
 * 활동 중인 회원만 나온다(탈퇴·정지 회원 제외).
 */
@Service
@Transactional(readOnly = true)
public class FollowListService {

    private final JdbcTemplate jdbc;
    private final SocialGuard guard;
    private final ProfileImageUrls profileImageUrls;
    private final BoardPaging paging;
    private final SocialProperties props;

    public FollowListService(JdbcTemplate jdbc, SocialGuard guard, ProfileImageUrls profileImageUrls,
                             BoardPaging paging, SocialProperties props) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.profileImageUrls = profileImageUrls;
        this.paging = paging;
        this.props = props;
    }

    public record UserRef(long id, String nickname, String profileImageUrl) {}

    public record UserPage(List<UserRef> items, int page, int size, long totalElements, int totalPages) {}

    public UserPage followers(long userId, Integer page) {
        return list(userId, page, "follower_id", "followee_id");
    }

    public UserPage following(long userId, Integer page) {
        return list(userId, page, "followee_id", "follower_id");
    }

    /** shown: 목록에 보일 회원 쪽 컬럼, owner: 목록 주인 쪽 컬럼(코드 안 상수만 들어간다). */
    private UserPage list(long userId, Integer page, String shown, String owner) {
        guard.requireTarget(userId);
        int p = paging.page(page);
        int size = props.list().pageSize();
        String from = " FROM follows f JOIN users u ON u.id = f." + shown + " AND u.status = 'ACTIVE' WHERE f." + owner
                + " = ?";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, userId);
        long totalElements = total == null ? 0 : total;
        List<UserRef> items = jdbc.query("SELECT u.id, u.nickname" + from + " ORDER BY f.created_at DESC, f."
                        + shown + " DESC LIMIT ? OFFSET ?",
                (rs, i) -> new UserRef(rs.getLong(1), rs.getString(2), profileImageUrls.of(rs.getLong(1))),
                userId, size, (long) (p - 1) * size);
        return new UserPage(items, p, size, totalElements, (int) ((totalElements + size - 1) / size));
    }
}
