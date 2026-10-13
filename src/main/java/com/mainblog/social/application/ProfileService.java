package com.mainblog.social.application;

import com.mainblog.common.config.SocialProperties;
import com.mainblog.common.error.InvalidInputException;
import com.mainblog.member.application.ProfileImageUrls;
import com.mainblog.social.application.SocialGuard.UserRow;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 프로필과 소개 (003 T018, SOC-03, FR-001~FR-005, D-102 #6~#8, research R-09).
 * 응답에는 닉네임·프로필 사진·소개만 넣고 이메일·이름·전화번호는 없다(FR-002, D-22).
 */
@Service
public class ProfileService {

    private static final String BLOG_COLUMNS = "SELECT b.id, b.address, b.name, b.visibility, b.status "
            + "FROM blog_members m JOIN blogs b ON b.id = m.blog_id "
            + "WHERE m.user_id = ? AND b.status IN ('ACTIVE', 'CLOSING') AND b.address IS NOT NULL";
    /** 다른 사람(비회원 포함)에게는 공개·숨기지 않은 블로그만(D-102 #6). */
    private static final String PUBLIC_ONLY = " AND b.visibility = 'PUBLIC' AND b.hidden = FALSE";

    private final JdbcTemplate jdbc;
    private final SocialGuard guard;
    private final ProfileImageUrls profileImageUrls;
    private final SocialProperties props;

    public ProfileService(JdbcTemplate jdbc, SocialGuard guard, ProfileImageUrls profileImageUrls,
                          SocialProperties props) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.profileImageUrls = profileImageUrls;
        this.props = props;
    }

    public record ProfileBlog(long id, String address, String name, String visibility, String status) {}

    /** 로그인했을 때만. 상대가 나를 차단했는지는 넣지 않는다(D-102 #11). */
    public record Viewer(boolean self, boolean following, boolean blocking) {}

    public record Profile(long id, String nickname, String profileImageUrl, String bio, List<ProfileBlog> ownedBlogs,
                          List<ProfileBlog> joinedBlogs, int followerCount, Viewer viewer) {}

    public record Bio(String bio) {}

    /** 없는 회원·탈퇴한 회원·메인 관리자 계정은 404(research 가정 1). */
    @Transactional(readOnly = true)
    public Profile profile(long userId, Long viewerId) {
        UserRow user = guard.requireTarget(userId);
        boolean self = viewerId != null && viewerId == userId;
        String filter = self ? "" : PUBLIC_ONLY;
        List<ProfileBlog> owned = blogs(userId, " AND m.role = 'OWNER'" + filter);
        List<ProfileBlog> joined = blogs(userId, " AND m.role IN ('MANAGER', 'MEMBER')" + filter);
        Viewer viewer = viewerId == null ? null
                : new Viewer(self, !self && follows(viewerId, userId), !self && guard.blocks(viewerId, userId));
        return new Profile(user.id(), user.nickname(), profileImageUrls.of(userId), user.bio(), owned, joined,
                followerCount(userId), viewer);
    }

    /** 앞뒤 공백을 빼고 0~max-length자(코드 포인트). 빈 값·null이면 지운다. 줄바꿈 허용(D-102 #7). */
    @Transactional
    public Bio updateBio(long userId, String raw) {
        String bio = raw == null ? "" : raw.strip();
        if (bio.codePointCount(0, bio.length()) > props.bio().maxLength()) {
            throw new InvalidInputException("bio", "Size");
        }
        String value = bio.isEmpty() ? null : bio;
        jdbc.update("UPDATE users SET bio = ? WHERE id = ? AND status = 'ACTIVE'", value, userId);
        return new Bio(value);
    }

    public int followerCount(long userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM follows f JOIN users u ON u.id = f.follower_id "
                + "WHERE f.followee_id = ? AND u.status = 'ACTIVE'", Integer.class, userId);
        return n == null ? 0 : n;
    }

    private boolean follows(long followerId, long followeeId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM follows WHERE follower_id = ? AND followee_id = ?",
                Integer.class, followerId, followeeId);
        return n != null && n > 0;
    }

    private List<ProfileBlog> blogs(long userId, String where) {
        return jdbc.query(BLOG_COLUMNS + where + " ORDER BY m.joined_at, b.id",
                (rs, i) -> new ProfileBlog(rs.getLong("id"), rs.getString("address"), rs.getString("name"),
                        rs.getString("visibility"), rs.getString("status")), userId);
    }
}
