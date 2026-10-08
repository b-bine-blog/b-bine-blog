package com.mainblog.common.storage;

import com.mainblog.common.error.BusinessException;
import com.mainblog.common.error.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * 이미지 메타데이터(EXIF 위치 정보 등) 제거 (T060, FR-043, SEC-08, R-11).
 * jpg·png·gif는 ImageIO로 픽셀만 읽어 다시 써서 메타데이터를 버린다(gif 움직임은 첫 장면만 남는다).
 * webp는 ImageIO가 읽지 못하므로 RIFF 청크에서 {@code EXIF}·{@code XMP }만 빼고 VP8X 표시도 지운다.
 */
@Component
public class ImageSanitizer {

    private static final float JPEG_QUALITY = 0.92f;
    private static final int VP8X_FLAG_EXIF = 0x08;
    private static final int VP8X_FLAG_XMP = 0x04;

    /**
     * @param maxPixels 가로×세로 한도. 작은 파일로 큰 그림을 풀게 해 메모리를 다 쓰게 하는 것을 막는다.
     */
    public byte[] sanitize(byte[] content, ImageType type, long maxPixels) {
        try {
            return type == ImageType.WEBP ? stripWebp(content) : reencode(content, type, maxPixels);
        } catch (IOException | RuntimeException e) {
            // 시그니처만 맞추고 내용이 깨진 파일
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    private static byte[] reencode(byte[] content, ImageType type, long maxPixels) throws IOException {
        BufferedImage image = read(content, maxPixels);
        if (type == ImageType.JPEG && image.getColorModel().hasAlpha()) {
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.createGraphics().drawImage(image, 0, 0, null);
            image = rgb;
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(type.extension());
        if (!writers.hasNext()) {
            throw new IOException("쓰기 도구 없음: " + type);
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (type == ImageType.JPEG) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            // 메타데이터 없이 픽셀만 쓴다.
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** 크기를 먼저 보고 한도 안일 때만 픽셀을 읽는다. gif는 첫 장면만 읽는다. */
    private static BufferedImage read(byte[] content, long maxPixels) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IOException("읽을 수 없는 이미지");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > maxPixels) {
                    throw new IOException("그림이 너무 큼");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    /** RIFF: "RIFF" size(LE) "WEBP" 뒤로 청크(fourcc + size(LE) + data + 홀수면 1바이트 채움). */
    static byte[] stripWebp(byte[] content) throws IOException {
        ByteBuffer in = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
        if (content.length < 12) {
            throw new IOException("짧은 RIFF");
        }
        long riffEnd = Math.min(content.length, 8L + Integer.toUnsignedLong(in.getInt(4)));
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int pos = 12;
        while (pos + 8 <= riffEnd) {
            String fourcc = new String(content, pos, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long size = Integer.toUnsignedLong(in.getInt(pos + 4));
            long padded = size + (size & 1);
            if (pos + 8 + size > riffEnd) {
                throw new IOException("청크 길이가 파일보다 김");
            }
            int chunkLength = (int) Math.min(8 + padded, riffEnd - pos);
            if (!"EXIF".equals(fourcc) && !"XMP ".equals(fourcc)) {
                byte[] chunk = java.util.Arrays.copyOfRange(content, pos, pos + chunkLength);
                if ("VP8X".equals(fourcc) && size >= 1) {
                    chunk[8] = (byte) (chunk[8] & ~(VP8X_FLAG_EXIF | VP8X_FLAG_XMP));
                }
                body.write(chunk);
            }
            pos += chunkLength;
        }
        byte[] chunks = body.toByteArray();
        ByteBuffer out = ByteBuffer.allocate(12 + chunks.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put(content, 0, 4).putInt(4 + chunks.length).put(content, 8, 4).put(chunks);
        return out.array();
    }
}
