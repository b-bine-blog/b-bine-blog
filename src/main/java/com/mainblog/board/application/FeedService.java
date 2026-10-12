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

    public enum Tab { LATEST, POPULAR, SUBSCRIBED, FOLLOWING }

    private static final String LATEST_ORDER = "p.created_at DESC, p.id DESC";
    private static final String POPULAR_ORDER = "p.like_count DESC, p.created_at DESC, p.id DESC";

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
            case SUBSCRIBED, FOLLOWING -> cursorFeed(tab, viewerId, cursor);
        };
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
