package com.mainblog.board.application;

import com.mainblog.board.application.BoardViews.Author;
import com.mainblog.common.storage.FileStorage;
import org.springframework.stereotype.Component;

/**
 * 작성자 표시 (004 T011, FR-016, D-100 #1·#2, research R-05).
 * 회원탈퇴가 블로그 탈퇴보다 앞선다. 글은 블로그를 떠나면 "탈퇴한 계정", 댓글은 닉네임을 그대로 쓴다.
 */
@Component
public class AuthorView {

    public static final String LEFT_BLOG_NAME = "탈퇴한 계정";
    public static final String WITHDRAWN_NAME = "탈퇴한 회원";

    /** 작성자의 프로필 사진 저장 이름을 함께 읽는 SQL 조각. users 별칭은 u. */
    public static final String PROFILE_NAME_SQL = "(SELECT pi.stored_name FROM images pi WHERE pi.owner_type = 'PROFILE' "
            + "AND pi.owner_id = u.id AND pi.deleted_at IS NULL ORDER BY pi.id DESC LIMIT 1)";

    private final FileStorage fileStorage;

    public AuthorView(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    /** 글 작성자. leftBlog = posts.author_left. */
    public Author ofPost(long userId, String nickname, String userStatus, String profileName, boolean leftBlog) {
        if ("WITHDRAWN".equals(userStatus)) {
            return new Author(null, WITHDRAWN_NAME, null, "WITHDRAWN");
        }
        if (leftBlog) {
            return new Author(null, LEFT_BLOG_NAME, null, "LEFT_BLOG");
        }
        return active(userId, nickname, profileName);
    }

    /** 댓글 작성자. 블로그를 떠나도 닉네임 그대로(D-100 #1). */
    public Author ofComment(long userId, String nickname, String userStatus, String profileName) {
        if ("WITHDRAWN".equals(userStatus)) {
            return new Author(null, WITHDRAWN_NAME, null, "WITHDRAWN");
        }
        return active(userId, nickname, profileName);
    }

    private Author active(long userId, String nickname, String profileName) {
        return new Author(userId, nickname, profileName == null ? null : fileStorage.url(profileName), "ACTIVE");
    }
}
