package com.mainblog.board.application;

import java.util.List;

/**
 * 보이는 글 SQL 조건을 한 곳에 모은다 (004 T011, FR-058, FR-061, FR-045, D-100 #16·#18, research R-04, R-14).
 * 별칭: posts p, blogs b. 값은 모두 바인딩 파라미터로 넘긴다.
 */
public final class PostVisibility {

    /** 공개 피드·검색·태그 목록: 공개·숨기지 않은·폐쇄되지 않은 블로그의 보이는 글. */
    public static final String PUBLIC_BLOG_POST = "p.status = 'VISIBLE' AND p.board_type = 'BLOG' "
            + "AND b.status IN ('ACTIVE', 'CLOSING') AND b.visibility = 'PUBLIC' AND b.hidden = FALSE";

    /** 블로그 안 목록: 그 블로그의 보이는 글(블로그 열람 확인은 BlogPermissionService가 먼저 한다). */
    public static final String BLOG_POST = "p.status = 'VISIBLE' AND p.board_type = 'BLOG' AND p.blog_id = ?";

    /** 메인 공지. */
    public static final String NOTICE_POST = "p.status = 'VISIBLE' AND p.board_type = 'NOTICE'";

    /**
     * 구독 탭: 구독한 블로그 가운데 볼 수 있는 블로그의 글. PUBLIC·LINK는 구독자도 보고, PRIVATE는 멤버만(R-04 *가정*).
     * 파라미터: 보는 회원 번호 2번.
     */
    public static final String SUBSCRIBED = "p.status = 'VISIBLE' AND p.board_type = 'BLOG' "
            + "AND b.status IN ('ACTIVE', 'CLOSING') AND b.hidden = FALSE "
            + "AND EXISTS (SELECT 1 FROM blog_subscriptions s WHERE s.blog_id = p.blog_id AND s.user_id = ?) "
            + "AND (b.visibility IN ('PUBLIC', 'LINK') "
            + "OR EXISTS (SELECT 1 FROM blog_members vm WHERE vm.blog_id = p.blog_id AND vm.user_id = ?))";

    /**
     * 팔로우 탭: 팔로우한 회원이 쓴 글 가운데 보는 회원이 볼 수 있는 블로그의 글. 블로그를 떠난 작성자의 글은 뺀다.
     * 파라미터: 보는 회원 번호 3번.
     */
    public static final String FOLLOWING = "p.status = 'VISIBLE' AND p.board_type = 'BLOG' AND p.author_left = FALSE "
            + "AND b.status IN ('ACTIVE', 'CLOSING') AND b.hidden = FALSE "
            + "AND EXISTS (SELECT 1 FROM follows f WHERE f.follower_id = ? AND f.followee_id = p.author_id) "
            + "AND (b.visibility = 'PUBLIC' "
            + "OR (b.visibility = 'LINK' AND EXISTS (SELECT 1 FROM blog_subscriptions s WHERE s.blog_id = p.blog_id AND s.user_id = ?)) "
            + "OR EXISTS (SELECT 1 FROM blog_members vm WHERE vm.blog_id = p.blog_id AND vm.user_id = ?))";

    /**
     * 보는 회원이 차단한 작성자의 글을 뺀다. 둘 다 그 블로그 멤버면 넣는다(FR-045). 파라미터: 보는 회원 번호 1번.
     */
    private static final String NOT_BLOCKED = " AND NOT EXISTS (SELECT 1 FROM user_blocks ub "
            + "WHERE ub.blocker_id = ? AND ub.blocked_id = p.author_id "
            + "AND NOT (p.blog_id IS NOT NULL "
            + "AND EXISTS (SELECT 1 FROM blog_members m1 WHERE m1.blog_id = p.blog_id AND m1.user_id = ub.blocker_id) "
            + "AND EXISTS (SELECT 1 FROM blog_members m2 WHERE m2.blog_id = p.blog_id AND m2.user_id = ub.blocked_id)))";

    private PostVisibility() {
    }

    /** 로그인한 회원이면 차단 조건을 붙인다. 비회원은 차단이 없다. */
    public static String notBlocked(Long viewerId, List<Object> args) {
        if (viewerId == null) {
            return "";
        }
        args.add(viewerId);
        return NOT_BLOCKED;
    }
}
