package com.li.lipicturecloud.application.companion;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.Set;

/**
 * 已通过二次授权和资源版本核验的受控图片快照。
 *
 * <p>它刻意不携带 URL、对象键或 COS 配置。{@code resourceBinding} 是 Provider 生成的不可逆
 * 资源绑定摘要，只供外发前重新核验同一对象；它不是 URL 或对象键。字节在构造和读取时都复制，
 * 调用方不能借由数组修改已通过核验的内容。</p>
 */
public record AuthorizedPictureContent(long pictureId, Instant resourceVersion, String mimeType,
                                       String resourceBinding, byte[] bytes) {

    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Pattern RESOURCE_BINDING = Pattern.compile("[0-9a-f]{64}|unverified-test-content");

    public AuthorizedPictureContent {
        if (pictureId <= 0) {
            throw new IllegalArgumentException("pictureId must be positive");
        }
        Objects.requireNonNull(resourceVersion, "resourceVersion");
        if (!SUPPORTED_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException("unsupported image mime type");
        }
        if (resourceBinding == null || !RESOURCE_BINDING.matcher(resourceBinding).matches()) {
            throw new IllegalArgumentException("resourceBinding must be an opaque SHA-256 digest");
        }
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("image bytes must not be empty");
        }
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * 为不需要调用真实授权 Provider 的本地单元测试保留的便利构造器。
     * 真实 Provider 一定会传入源资源的 SHA-256 绑定摘要。
     */
    public AuthorizedPictureContent(long pictureId, Instant resourceVersion, String mimeType, byte[] bytes) {
        this(pictureId, resourceVersion, mimeType, "unverified-test-content", bytes);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
