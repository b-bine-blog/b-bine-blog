package com.mainblog.board.application;

import com.mainblog.board.application.BoardViews.FeedResult;
import com.mainblog.board.application.BoardViews.PostPage;
import com.mainblog.board.application.BoardViews.PostSummary;
import com.mainblog.common.config.BoardProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메인 피드 (004 T030, FR-061~FR-064, D-73, D-89, D-100 #18, research R-14).
 * 볼 때마다 쿼리로 모아 삭제·차단·비공개 전환이 다음 조회에 바로 반영된다.
 * 최신·인기는 공개 블로그 글의 번호 페이지, 구독·팔로우는 최근 30일 글을 20개씩 cursor로 이어 받는다.
 */
@Service
public class FeedService {

    public enum Tab { LATEST, POPULAR, INTEREST, SUBSCRIBED, FOLLOWING }

    private static final String LATEST_ORDER = "p.created_at DESC, p.id DESC";
    private static final String POPULAR_ORDER = "p.like_count DESC, p.created_at DESC, p.id DESC";

    // 관심 탭(D-137) 순위: 0 = 좋아요를 누른 적 있는 구독 블로그, 1 = 다른 구독 블로그, 2 = 팔로우한 회원, 3 = 그 밖의 공개 글
    private static final String SUBSCRIBED_BLOG = "EXISTS (SELECT 1 FROM blog_subscriptions s2 "
            + "WHERE s2.blog_id = p.blog_id AND s2.user_id = ?)";
    private static final String FOLLOWED_AUTHOR = "EXISTS (SELECT 1 FROM follows f2 "
            + "WHERE f2.follower_id = ? AND f2.followee_id = p.author_id)";
    private static final String MY_LIKES_IN_BLOG = "(SELECT COUNT(*) FROM post_likes pl JOIN posts lp ON lp.id = pl.post_id "
            + "WHERE pl.user_id = ? AND lp.blog_id = p.blog_id)";
    private static final String INTEREST_RANK = "(CASE WHEN " + SUBSCRIBED_BLOG + " AND " + MY_LIKES_IN_BLOG + " > 0 THEN 0 "
            + "WHEN " + SUBSCRIBED_BLOG + " THEN 1 WHEN " + FOLLOWED_AUTHOR + " THEN 2 ELSE 3 END)";

    private final PostSummaryReader reader;
    private final BoardPaging paging;
    private final BoardProperties props;
    private final Clock clock;

    public FeedService(PostSummaryReader reader, BoardPaging paging, BoardProperties props, Clock clock) {
        this.reader = reader;
        this.paging = paging;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FeedResult feed(String rawTab, Long viewerId, Integer page, Integer size, String cursor) {
        Tab tab = parse(rawTab);
        return switch (tab) {
            case LATEST, POPULAR -> {
                List<Object> args = new ArrayList<>();
                String where = PostVisibility.PUBLIC_BLOG_POST + PostVisibility.notBlocked(viewerId, args);
                PostPage result = reader.page(where, args, tab == Tab.LATEST ? LATEST_ORDER : POPULAR_ORDER,
                        paging.page(page), paging.size(size));
                yield new FeedResult(tab.name(), result.items(), result.page(), result.totalPages(), null,
                        tab == Tab.LATEST && result.page() == 1 ? pinnedNotices() : List.of());
            }
            case INTEREST -> interest(viewerId, page, size);
            case SUBSCRIBED, FOLLOWING -> cursorFeed(tab, viewerId, cursor);
        };
    }

    /**
     * 관심 탭(D-137): 최근 글(구독·팔로우 탭과 같은 기간) 가운데
     * 1) 내가 좋아요를 많이 누른 구독 블로그의 글(누른 수가 많은 블로그부터, 같으면 최신순),
     * 2) 다른 구독 블로그의 글(최신순),
     * 3) 팔로우한 회원의 글(최신순),
     * 4) 그 밖의 공개 글(좋아요 많은 순, 같으면 최신순).
     */
    private FeedResult interest(Long viewerId, Integer page, Integer size) {
        if (viewerId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        List<Object> args = new ArrayList<>();
        String where = "((" + PostVisibility.PUBLIC_BLOG_POST + ") OR (" + PostVisibility.SUBSCRIBED + ") OR ("
                + PostVisibility.FOLLOWING + ")) AND p.created_at >= ?";
        args.addAll(List.of(viewerId, viewerId, viewerId, viewerId, viewerId));
        args.add(LocalDateTime.now(clock).minusDays(props.feed().recentDays()));
        where += PostVisibility.notBlocked(viewerId, args);
        String order = INTEREST_RANK + ", CASE WHEN " + INTEREST_RANK + " = 0 THEN " + MY_LIKES_IN_BLOG + " ELSE 0 END DESC, "
                + "CASE WHEN " + INTEREST_RANK + " < 3 THEN p.created_at END DESC, " + POPULAR_ORDER;
        List<Object> orderArgs = new ArrayList<>();
        List<Object> rank = List.of(viewerId, viewerId, viewerId, viewerId);  // 구독·좋아요 수·구독·팔로우
        orderArgs.addAll(rank);
        orderArgs.addAll(rank);
        orderArgs.add(viewerId);
        orderArgs.addAll(rank);
        PostPage result = reader.page(where, args, order, orderArgs, paging.page(page), paging.size(size));
        return new FeedResult(Tab.INTEREST.name(), result.items(), result.page(), result.totalPages(), null, List.of());
    }

    private FeedResult cursorFeed(Tab tab, Long viewerId, String cursor) {
        if (viewerId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        if (tab == Tab.SUBSCRIBED) {
            where.append(PostVisibility.SUBSCRIBED);
            args.add(viewerId);
            args.add(viewerId);
        } else {
            where.append(PostVisibility.FOLLOWING);
            args.add(viewerId);
            args.add(viewerId);
            args.add(viewerId);
        }
        where.append(" AND p.created_at >= ?");
        args.add(LocalDateTime.now(clock).minusDays(props.feed().recentDays()));
        Cursor c = Cursor.parse(cursor);
        if (c != null) {
            where.append(" AND (p.created_at < ? OR (p.created_at = ? AND p.id < ?))");
            args.add(c.createdAt());
            args.add(c.createdAt());
            args.add(c.id());
        }
        where.append(PostVisibility.notBlocked(viewerId, args));
        int limit = props.feed().pageSize();
        List<PostSummary> rows = reader.list(where.toString(), args, LATEST_ORDER, limit + 1, 0);
        String next = null;
        if (rows.size() > limit) {
            rows = rows.subList(0, limit);
            PostSummary last = rows.get(limit - 1);
            next = new Cursor(last.createdAt(), last.id()).format();
        }
        return new FeedResult(tab.name(), rows, null, null, next, List.of());
    }

    /** 최근 메인 공지 몇 개를 최신 글 위에 고정한다(D-109). 개수는 설정값(board.feed.pinned-notices). */
    private List<PostSummary> pinnedNotices() {
        int count = props.feed().pinnedNotices();
        if (count == 0) {
            return List.of();
        }
        return reader.list(PostVisibility.NOTICE_POST, List.of(), LATEST_ORDER, count, 0);
    }

    private static Tab parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Tab.LATEST;
        }
        try {
            return Tab.valueOf(raw.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("tab", "Pattern");
        }
    }

    /** "작성 시각_글 번호". 같은 시각의 글도 번호로 이어 받는다. */
    record Cursor(LocalDateTime createdAt, long id) {

        String format() {
            return createdAt + "_" + id;
        }

        static Cursor parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            int sep = raw.lastIndexOf('_');
            try {
                return new Cursor(LocalDateTime.parse(raw.substring(0, sep)), Long.parseLong(raw.substring(sep + 1)));
            } catch (RuntimeException e) {
                throw new InvalidInputException("cursor", "Pattern");
            }
        }
    }
}
