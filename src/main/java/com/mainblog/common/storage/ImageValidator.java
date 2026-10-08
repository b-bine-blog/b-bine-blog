package com.mainblog.common.storage;

import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 이미지 파일 확인 (T060, FR-043, 6.3, SEC-08, R-11).
 * 확장자, MIME 타입, 파일 앞부분 시그니처(매직 넘버)가 모두 같은 형식을 가리켜야 통과한다. 크기 한도는 호출한 쪽 설정값(D-68).
 */
@Component
public class ImageValidator {

    private static final Map<String, ImageType> EXTENSIONS = Map.of(
            "jpg", ImageType.JPEG, "jpeg", ImageType.JPEG, "png", ImageType.PNG,
            "gif", ImageType.GIF, "webp", ImageType.WEBP);

    public ImageType validate(byte[] content, String originalFilename, String contentType, long maxBytes) {
        if (content == null || content.length == 0) {
            throw notAllowed();
        }
        if (content.length > maxBytes) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        ImageType byExtension = EXTENSIONS.get(extension(originalFilename));
        if (byExtension == null) {
            throw notAllowed();
        }
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].strip();
        if (!byExtension.mimeType().equals(mime)) {
            throw notAllowed();
        }
        if (detect(content) != byExtension) {
            throw notAllowed();
        }
        return byExtension;
    }

    /** 매직 넘버: FFD8FF / 89504E47 0D0A1A0A / GIF87a·GIF89a / RIFF....WEBP. */
    static ImageType detect(byte[] b) {
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) {
            return ImageType.JPEG;
        }
        if (startsWith(b, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return ImageType.PNG;
        }
        if (startsWith(b, 0, 'G', 'I', 'F', '8') && b.length > 5 && (b[4] == '7' || b[4] == '9') && b[5] == 'a') {
            return ImageType.GIF;
        }
        if (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P')) {
            return ImageType.WEBP;
        }
        return null;
    }

    private static boolean startsWith(byte[] b, int offset, int... expected) {
        if (b.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((b[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static BusinessException notAllowed() {
        return new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
    }
}
