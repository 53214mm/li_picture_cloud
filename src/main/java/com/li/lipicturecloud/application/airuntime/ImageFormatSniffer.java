package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CreationFusionImage;

import java.util.Arrays;

/**
 * 按魔数嗅探融合生成结果的图片格式，只识别 jpeg/png/webp。
 * 不信任供应商返回的 mime 声明，字节级判定后才允许进入暂存与保存管线。
 */
public final class ImageFormatSniffer {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private ImageFormatSniffer() {
    }

    public static String detect(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            throw new IllegalArgumentException("image bytes are too short to identify");
        }
        if (bytes.length > CreationFusionImage.MAX_BYTES) {
            throw new IllegalArgumentException("image bytes exceed the fusion staging limit");
        }
        if (startsWith(bytes, PNG_SIGNATURE)) {
            return "image/png";
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (startsWith(bytes, new byte[]{'R', 'I', 'F', 'F'})
                && startsWith(bytes, 8, new byte[]{'W', 'E', 'B', 'P'})) {
            return "image/webp";
        }
        throw new IllegalArgumentException("unsupported fusion image format");
    }

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        return startsWith(bytes, 0, signature);
    }

    private static boolean startsWith(byte[] bytes, int offset, byte[] signature) {
        if (offset + signature.length > bytes.length) {
            return false;
        }
        return Arrays.equals(Arrays.copyOfRange(bytes, offset, offset + signature.length), signature);
    }
}
