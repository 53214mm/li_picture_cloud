package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CreationFusionImage;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * 融合作品回库请求：字节 + 目标空间 + 作品名。构造即校验并防御性复制字节。
 */
public record FusionArtworkSaveRequest(
        long userId,
        long spaceId,
        String name,
        String mimeType,
        byte[] bytes) {

    public static final int MAX_NAME_CODE_POINTS = 128;
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    public FusionArtworkSaveRequest {
        if (userId <= 0 || spaceId <= 0) {
            throw new IllegalArgumentException("invalid fusion artwork identity");
        }
        if (name == null || name.isBlank()) {
            name = "融合作品";
        }
        name = name.strip();
        if (name.codePointCount(0, name.length()) > MAX_NAME_CODE_POINTS) {
            throw new IllegalArgumentException("fusion artwork name is too long");
        }
        if (name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("fusion artwork name must be safe plain text");
        }
        if (mimeType == null || !SUPPORTED_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException("unsupported fusion artwork mime type");
        }
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0 || bytes.length > CreationFusionImage.MAX_BYTES) {
            throw new IllegalArgumentException("invalid fusion artwork byte size");
        }
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
