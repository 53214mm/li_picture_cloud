package com.li.lipicturecloud.domain.airuntime;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * 融合生成图片的暂存（每任务一份）：保存回图库前的字节安全中转。
 * 只接受 jpeg/png/webp，上限 16 MiB；读取时防御性复制，调用方无法修改已存字节。
 */
public record CreationFusionImage(
        Long id,
        long taskId,
        String mimeType,
        byte[] bytes,
        Instant createdTime) {

    public static final long MAX_BYTES = 16L * 1024 * 1024;
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    public CreationFusionImage {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId must be positive");
        }
        if (mimeType == null || !SUPPORTED_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException("unsupported fusion image mime type");
        }
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("fusion image bytes must be 1.." + MAX_BYTES + " bytes");
        }
        bytes = Arrays.copyOf(bytes, bytes.length);
        Objects.requireNonNull(createdTime, "createdTime");
    }

    public static CreationFusionImage create(long taskId, String mimeType, byte[] bytes,
                                             Instant now) {
        return new CreationFusionImage(null, taskId, mimeType, bytes, now);
    }

    public CreationFusionImage withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new CreationFusionImage(persistedId, taskId, mimeType, bytes, createdTime);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
