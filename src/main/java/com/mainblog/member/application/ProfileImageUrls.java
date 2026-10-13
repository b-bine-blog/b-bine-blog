package com.mainblog.member.application;

import com.mainblog.common.storage.FileStorage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 회원의 지금 프로필 사진 주소(FR-043). 없으면 null. */
@Component
public class ProfileImageUrls {

    private final JdbcTemplate jdbc;
    private final FileStorage fileStorage;

    public ProfileImageUrls(JdbcTemplate jdbc, FileStorage fileStorage) {
        this.jdbc = jdbc;
        this.fileStorage = fileStorage;
    }

    public String of(Long userId) {
        return jdbc.query("SELECT stored_name FROM images WHERE owner_type = 'PROFILE' AND owner_id = ? "
                        + "AND deleted_at IS NULL ORDER BY id DESC LIMIT 1",
                rs -> rs.next() ? fileStorage.url(rs.getString(1)) : null, userId);
    }
}
