package com.mainblog.board.application;

import com.mainblog.board.application.BoardViews.UploadedImage;
import com.mainblog.board.application.BoardViews.UploadedImages;
import com.mainblog.common.config.BoardProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.markdown.MarkdownText;
import com.mainblog.common.storage.FileStorage;
import com.mainblog.common.storage.ImageSanitizer;
import com.mainblog.common.storage.ImageType;
import com.mainblog.common.storage.ImageValidator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 글 이미지 올리기와 글에 잇기 (004 T033, FR-029~FR-035, SEC-08, D-75, research R-03).
 * 올리면 소속 없는(owner_id NULL) images 행이 생기고, 글을 저장할 때 본문에 있는 본인 이미지만 그 글에 잇는다.
 */
@Service
public class PostImageService {

    private static final Logger log = LoggerFactory.getLogger(PostImageService.class);

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final ImageValidator validator;
    private final ImageSanitizer sanitizer;
    private final FileStorage fileStorage;
    private final BoardProperties props;
    private final Clock clock;

    public PostImageService(JdbcTemplate jdbc, ImageValidator validator, ImageSanitizer sanitizer,
                            FileStorage fileStorage, BoardProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
        this.validator = validator;
        this.sanitizer = sanitizer;
        this.fileStorage = fileStorage;
        this.props = props;
        this.clock = clock;
    }

    /** 권한 확인을 마친 뒤 부른다. 한 번에 10장·10MB, 장당 3MB. 하나라도 틀리면 모두 되돌린다. */
    public UploadedImages upload(long uploaderId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        if (files.size() > props.image().maxPerPost()) {
            throw new BusinessException(ErrorCode.POST_IMAGE_LIMIT);
        }
        long total = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (total > props.image().maxRequestSize().toBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        List<UploadedImage> result = new ArrayList<>();
        for (MultipartFile file : files) {
            byte[] content;
            try {
                content = file.getBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            String originalName = baseName(file.getOriginalFilename());
            ImageType type = validator.validate(content, originalName, file.getContentType(),
                    props.image().maxSize().toBytes());
            byte[] clean = sanitizer.sanitize(content, type, props.image().maxPixels());
            String storedName;
            try {
                storedName = fileStorage.save(clean, type.extension());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            deleteFileIfRolledBack(storedName);
            long id = insert(uploaderId, storedName, originalName, type.mimeType(), clean.length);
            result.add(new UploadedImage(id, fileStorage.url(storedName)));
        }
        return new UploadedImages(result);
    }

    /**
     * 본문의 /files/ 이미지 가운데 이 회원이 올렸고 소속이 없거나 이 글인 것을 글에 잇는다.
     * 본문에서 빠진 이 글의 이미지는 소속을 비워 정리 대상으로 만든다. 서로 다른 이미지가 10장을 넘으면 400.
     */
    public void link(long postId, long uploaderId, String safeHtml) {
        List<String> names = MarkdownText.fileImageNames(safeHtml);
        List<String> mine = names.isEmpty() ? List.of() : named.queryForList(
                "SELECT stored_name FROM images WHERE stored_name IN (:names) AND owner_type = 'POST' "
                        + "AND uploader_id = :uploader AND deleted_at IS NULL "
                        + "AND (owner_id IS NULL OR owner_id = :post)",
                new MapSqlParameterSource("names", names).addValue("uploader", uploaderId).addValue("post", postId),
                String.class);
        if (mine.size() > props.image().maxPerPost()) {
            throw new BusinessException(ErrorCode.POST_IMAGE_LIMIT);
        }
        MapSqlParameterSource p = new MapSqlParameterSource("post", postId);
        if (mine.isEmpty()) {
            named.update("UPDATE images SET owner_id = NULL WHERE owner_type = 'POST' AND owner_id = :post", p);
            return;
        }
        p.addValue("names", mine);
        named.update("UPDATE images SET owner_id = NULL WHERE owner_type = 'POST' AND owner_id = :post "
                + "AND stored_name NOT IN (:names)", p);
        named.update("UPDATE images SET owner_id = :post WHERE owner_type = 'POST' AND stored_name IN (:names)", p);
    }

    private long insert(long uploaderId, String storedName, String originalName, String contentType, int size) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO images (uploader_id, owner_type, owner_id, "
                    + "stored_name, original_name, content_type, size_bytes, created_at, updated_at) "
                    + "VALUES (?, 'POST', NULL, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, uploaderId);
            ps.setString(2, storedName);
            ps.setString(3, originalName);
            ps.setString(4, contentType);
            ps.setInt(5, size);
            ps.setTimestamp(6, now);
            ps.setTimestamp(7, now);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    /** 경로 부분을 버린 파일 이름(255자까지). 저장 이름은 UUID라서 이 값은 기록용이다(SEC-08). */
    static String baseName(String name) {
        if (name == null || name.isBlank()) {
            return "image";
        }
        String s = name.replace('\\', '/');
        s = s.substring(s.lastIndexOf('/') + 1);
        return s.length() > 255 ? s.substring(s.length() - 255) : s;
    }

    private void deleteFileIfRolledBack(String storedName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        fileStorage.delete(storedName);
                    } catch (IOException e) {
                        log.warn("되돌린 글 이미지 파일 삭제 실패: {}", e.getClass().getSimpleName());
                    }
                }
            }
        });
    }
}
