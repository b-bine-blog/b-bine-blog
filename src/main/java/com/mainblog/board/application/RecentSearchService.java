package com.mainblog.board.application;

import com.mainblog.board.application.BoardViews.RecentSearch;
import com.mainblog.common.config.BoardProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최근 검색어 (004 T043, FR-059, D-100 #17, research R-13). 본인에게만 최근 10개.
 * 같은 검색어는 시각만 바꿔 맨 앞으로 옮기고(중복 없음), 넘치는 오래된 것은 지운다. 비회원은 저장하지 않는다.
 */
@Service
public class RecentSearchService {

    private final JdbcTemplate jdbc;
    private final BoardProperties props;
    private final Clock clock;

    public RecentSearchService(JdbcTemplate jdbc, BoardProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public void remember(long userId, String keyword) {
        LocalDateTime now = LocalDateTime.now(clock);
        // 같은 회원의 검색어 저장을 차례로 처리해 같은 말이 두 줄 생기지 않게 한다.
        jdbc.query("SELECT id FROM users WHERE id = ? FOR UPDATE", rs -> null, userId);
        int updated = jdbc.update("UPDATE recent_searches SET searched_at = ?, updated_at = ? WHERE user_id = ? "
                + "AND keyword = ?", now, now, userId, keyword);
        if (updated == 0) {
            jdbc.update("INSERT INTO recent_searches (user_id, keyword, searched_at, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?)", userId, keyword, now, now, now);
        }
        List<Long> extra = jdbc.queryForList("SELECT id FROM recent_searches WHERE user_id = ? "
                + "ORDER BY searched_at DESC, id DESC LIMIT 1000 OFFSET ?", Long.class, userId, props.search().recentMax());
        for (Long id : extra) {
            jdbc.update("DELETE FROM recent_searches WHERE id = ?", id);
        }
    }

    @Transactional(readOnly = true)
    public List<RecentSearch> list(long userId) {
        return jdbc.query("SELECT id, keyword, searched_at FROM recent_searches WHERE user_id = ? "
                        + "ORDER BY searched_at DESC, id DESC LIMIT ?",
                (rs, i) -> new RecentSearch(rs.getLong("id"), rs.getString("keyword"),
                        rs.getTimestamp("searched_at").toLocalDateTime()), userId, props.search().recentMax());
    }

    /** 남의 것이면 404. */
    @Transactional
    public void delete(long userId, long id) {
        if (jdbc.update("DELETE FROM recent_searches WHERE id = ? AND user_id = ?", id, userId) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    @Transactional
    public void deleteAll(long userId) {
        jdbc.update("DELETE FROM recent_searches WHERE user_id = ?", userId);
    }
}
