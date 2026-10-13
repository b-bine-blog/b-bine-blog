package com.mainblog.blog.application;

import com.mainblog.blog.domain.Blog;
import com.mainblog.blog.domain.BlogCoverImage;
import com.mainblog.blog.domain.BlogCoverImageRepository;
import com.mainblog.blog.domain.BlogImageKind;
import com.mainblog.blog.domain.BlogRepository;
import com.mainblog.common.config.BlogProperties;
import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import com.mainblog.common.storage.FileStorage;
import com.mainblog.common.storage.ImageSanitizer;
import com.mainblog.common.storage.ImageType;
import com.mainblog.common.storage.ImageValidator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 블로그 대표 이미지와 배경 이미지 (T026, FR-007, 6.3, SEC-08, research R-05, D-121).
 * 배경 이미지는 대표 이미지와 같은 크기 제한(blog.cover-image)과 검사를 쓴다.
 * 001 프로필 사진과 같은 검사(확장자·MIME·파일 앞부분)와 재인코딩을 거쳐 UUID 이름으로 저장한다.
 * 바꾸거나 지운 이미지는 deleted_at을 채우고 30일 뒤 04:00 정리에서 파일까지 지운다.
 */
@Service
public class BlogCoverImageService {

    private static final Logger log = LoggerFactory.getLogger(BlogCoverImageService.class);

    private final BlogRepository blogRepository;
    private final BlogCoverImageRepository repository;
    private final BlogPermissionService permissionService;
    private final ImageValidator imageValidator;
    private final ImageSanitizer imageSanitizer;
    private final FileStorage fileStorage;
    private final BlogProperties properties;
    private final Clock clock;

    public BlogCoverImageService(BlogRepository blogRepository, BlogCoverImageRepository repository,
                                 BlogPermissionService permissionService, ImageValidator imageValidator,
                                 ImageSanitizer imageSanitizer, FileStorage fileStorage, BlogProperties properties,
                                 Clock clock) {
        this.blogRepository = blogRepository;
        this.repository = repository;
        this.permissionService = permissionService;
        this.imageValidator = imageValidator;
        this.imageSanitizer = imageSanitizer;
        this.fileStorage = fileStorage;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public String change(BlogImageKind kind, long blogId, long userId, byte[] content, String originalFilename, String contentType) {
        lockAndCheck(blogId, userId);
        ImageType type = imageValidator.validate(content, originalFilename, contentType,
                properties.coverImage().maxSize().toBytes());
        byte[] clean = imageSanitizer.sanitize(content, type, properties.coverImage().maxPixels());
        String storedName;
        try {
            storedName = fileStorage.save(clean, type.extension());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        deleteFileIfRolledBack(storedName);
        LocalDateTime now = LocalDateTime.now(clock);
        repository.findAllByOwnerTypeAndOwnerIdAndDeletedAtIsNull(kind.ownerType(), blogId).forEach(old -> old.delete(now));
        repository.save(new BlogCoverImage(kind, blogId, userId, storedName, originalFilename, type.mimeType(),
                clean.length));
        return fileStorage.url(storedName);
    }

    @Transactional
    public void delete(BlogImageKind kind, long blogId, long userId) {
        lockAndCheck(blogId, userId);
        LocalDateTime now = LocalDateTime.now(clock);
        repository.findAllByOwnerTypeAndOwnerIdAndDeletedAtIsNull(kind.ownerType(), blogId).forEach(old -> old.delete(now));
    }

    private void lockAndCheck(long blogId, long userId) {
        Blog blog = blogRepository.findByIdForUpdate(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
        permissionService.require(blog, userId, BlogPermission.BLOG_INFO);
    }

    private void deleteFileIfRolledBack(String storedName) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        fileStorage.delete(storedName);
                    } catch (IOException e) {
                        log.warn("되돌린 대표 이미지 파일 삭제 실패: {}", e.getClass().getSimpleName());
                    }
                }
            }
        });
    }
}
