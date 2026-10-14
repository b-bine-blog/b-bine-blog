package com.mainblog.board.application;

import com.mainblog.common.config.BoardProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.error.InvalidInputException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메인 피드의 태그 탭 (D-135). 팔로우 옆 + 로 태그를 더하면 그 태그 글 목록이 탭이 된다.
 * 본인 계정에 저장해 어느 기기에서나 같다. 회원당 최대 개수는 설정값. 태그 이름은 글 태그와 같은 규칙으로 정리한다.
 */
@Service
public class FeedTagService {

    private final JdbcTemplate jdbc;
    private final PostTagService tagService;
    private final BoardProperties props;
    private final Clock clock;

    public FeedTagService(JdbcTemplate jdbc, PostTagService tagService, BoardProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.tagService = tagService;
        this.props = props;
        this.clock = clock;
    }

    /** 추가한 순서대로. */
    @Transactional(readOnly = true)
    public List<String> list(long userId) {
        return jdbc.queryForList("SELECT t.name FROM feed_tags f JOIN tags t ON t.id = f.tag_id WHERE f.user_id = ? "
                + "ORDER BY f.id", String.class, userId);
    }

    /** 이미 있으면 그대로. 규칙에 맞지 않으면 400, 개수를 넘으면 409. */
    @Transactional
    public List<String> add(long userId, String rawName) {
        String tag = normalize(rawName);
        // 같은 회원의 추가를 차례로 처리해 동시에 눌러도 개수를 넘지 않게 한다.
        jdbc.query("SELECT id FROM users WHERE id = ? FOR UPDATE", rs -> null, userId);
        jdbc.update("INSERT IGNORE INTO tags (name) VALUES (?)", tag);
        Long tagId = jdbc.queryForObject("SELECT id FROM tags WHERE name = ?", Long.class, tag);
        Integer has = jdbc.queryForObject("SELECT COUNT(*) FROM feed_tags WHERE user_id = ? AND tag_id = ?",
                Integer.class, userId, tagId);
        if (has == null || has == 0) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM feed_tags WHERE user_id = ?", Integer.class, userId);
            if (count != null && count >= props.feed().tagMaxCount()) {
                throw new BusinessException(ErrorCode.FEED_TAG_LIMIT_EXCEEDED);
            }
            LocalDateTime now = LocalDateTime.now(clock);
            jdbc.update("INSERT INTO feed_tags (user_id, tag_id, created_at, updated_at) VALUES (?, ?, ?, ?)",
                    userId, tagId, now, now);
        }
        return list(userId);
    }

    /** 없으면 아무 일도 없다. */
    @Transactional
    public void remove(long userId, String rawName) {
        String tag = normalize(rawName);
        jdbc.update("DELETE f FROM feed_tags f JOIN tags t ON t.id = f.tag_id WHERE f.user_id = ? AND t.name = ?",
                userId, tag);
    }

    private String normalize(String rawName) {
        String tag = tagService.normalizeOne(rawName);
        if (tag == null) {
            throw new InvalidInputException("name", "Pattern");
        }
        return tag;
    }
}
