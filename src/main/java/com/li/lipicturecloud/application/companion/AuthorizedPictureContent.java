package com.li.lipicturecloud.application.companion;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * 已通过二次授权和资源版本核验的受控图片快照。
 *
 * <p>它刻意不携带 URL、对象键或 COS 配置。字节在构造和读取时都复制，调用方不能借由数组
 * 修改已通过核验的内容。</p>
 */
public record AuthorizedPictureContent(long pictureId, Instant resourceVersion, String mimeType, byte[] bytes) {

    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    public AuthorizedPictureContent {
        if (pictureId <= 0) {
            throw new IllegalArgumentException("pictureId must be positive");
        }
        Objects.requireNonNull(resourceVersion, "resourceVersion");
        if (!SUPPORTED_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException("unsupported image mime type");
        }
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("image bytes must not be empty");
        }
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
