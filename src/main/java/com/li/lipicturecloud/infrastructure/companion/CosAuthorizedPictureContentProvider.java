package com.li.lipicturecloud.infrastructure.companion;

import com.li.lipicturecloud.application.companion.AuthorizedPictureContent;
import com.li.lipicturecloud.application.companion.AuthorizedPictureContentProvider;
import com.li.lipicturecloud.application.companion.AuthorizedPictureRef;
import com.li.lipicturecloud.application.companion.VisionContentException;
import com.li.lipicturecloud.config.CosClientConfig;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.CosManager;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.repository.PictureRepository;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;

/**
 * 只从已配置 COS 主机受控下载图片像素，并在下载后再次确认该像素仍属于同一授权资源。
 */
@Component
public class CosAuthorizedPictureContentProvider implements AuthorizedPictureContentProvider {

    private static final int BUFFER_SIZE = 8 * 1024;

    private final PictureRepository pictures;
    private final SpaceAuthorizationAccessService authorization;
    private final CosManager cos;
    private final URI configuredCosHost;

    public CosAuthorizedPictureContentProvider(PictureRepository pictures,
                                               SpaceAuthorizationAccessService authorization,
                                               CosManager cos,
                                               CosClientConfig cosConfiguration) {
        this.pictures = Objects.requireNonNull(pictures, "pictures");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.cos = Objects.requireNonNull(cos, "cos");
        this.configuredCosHost = parseConfiguredHost(Objects.requireNonNull(cosConfiguration, "cosConfiguration").getHost());
    }

    @Override
    public AuthorizedPictureContent load(AuthorizedPictureRef reference, long maxBytes) {
        Objects.requireNonNull(reference, "reference");
        if (maxBytes <= 0 || maxBytes > Integer.MAX_VALUE - 8L) {
            throw new IllegalArgumentException("maxBytes must be between 1 and 2147483639");
        }

        PictureSnapshot before = snapshotOf(loadPicture(reference.pictureId()));
        authorization.checkForUser(PICTURE_VIEW, reference.pictureId(), reference.subject().userId());
        String objectKey = objectKeyFromConfiguredHost(before.sourceUrl());
        String mimeType = mimeTypeFor(objectKey);
        byte[] bytes = downloadBounded(objectKey, maxBytes);
        validateMimeSignature(mimeType, bytes);

        PictureSnapshot after = snapshotOf(loadPicture(reference.pictureId()));
        authorization.checkForUser(PICTURE_VIEW, reference.pictureId(), reference.subject().userId());
        if (!before.equals(after)) {
            // 已下载的旧对象可能来自被移动、替换或删除前的版本，绝不继续外发。
            throw stateChanged();
        }
        return new AuthorizedPictureContent(before.pictureId(), before.resourceVersion(), mimeType,
                resourceBinding(before.sourceUrl()), bytes);
    }

    @Override
    public void verifyStillAuthorized(AuthorizedPictureRef reference, AuthorizedPictureContent content) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(content, "content");
        if (reference.pictureId() != content.pictureId()) {
            throw stateChanged();
        }
        PictureSnapshot current = snapshotOf(loadPicture(reference.pictureId()));
        authorization.checkForUser(PICTURE_VIEW, reference.pictureId(), reference.subject().userId());
        String mimeType = mimeTypeFor(objectKeyFromConfiguredHost(current.sourceUrl()));
        if (current.pictureId() != content.pictureId()
                || !current.resourceVersion().equals(content.resourceVersion())
                || !resourceBinding(current.sourceUrl()).equals(content.resourceBinding())
                || !mimeType.equals(content.mimeType())) {
            throw stateChanged();
        }
    }

    private Picture loadPicture(long pictureId) {
        return pictures.findById(pictureId).orElseThrow(() ->
                new BusinessException(ErrorCode.NO_AUTH_ERROR, "图片不可用或无权访问"));
    }

    private PictureSnapshot snapshotOf(Picture picture) {
        Long pictureId = picture.getId();
        if (pictureId == null || pictureId <= 0) {
            throw unavailable();
        }
        String sourceUrl = preferredObjectUrl(picture);
        Instant resourceVersion = resourceVersion(picture.getUpdateTime());
        return new PictureSnapshot(pictureId, sourceUrl, resourceVersion);
    }

    private String preferredObjectUrl(Picture picture) {
        if (hasText(picture.getOriginalUrl())) {
            return picture.getOriginalUrl();
        }
        if (hasText(picture.getUrl())) {
            return picture.getUrl();
        }
        throw unavailable();
    }

    private byte[] downloadBounded(String objectKey, long maxBytes) {
        try (COSObject object = cos.getObject(objectKey)) {
            if (object == null || object.getObjectContent() == null) {
                throw unavailable();
            }
            try (COSObjectInputStream input = object.getObjectContent();
                 ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(maxBytes, BUFFER_SIZE))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long total = 0;
                while (total < maxBytes) {
                    int maximumRead = (int) Math.min(buffer.length, maxBytes - total);
                    int read = input.read(buffer, 0, maximumRead);
                    if (read == -1) {
                        return nonEmpty(output.toByteArray());
                    }
                    output.write(buffer, 0, read);
                    total += read;
                }
                if (input.read() != -1) {
                    throw new VisionContentException("VISION_IMAGE_TOO_LARGE", "图片内容超过视觉营养大小上限");
                }
                return nonEmpty(output.toByteArray());
            }
        } catch (VisionContentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            // COS SDK 的异常可能带对象键或服务端细节，使用稳定的安全错误替代它。
            throw unavailable();
        }
    }

    private String objectKeyFromConfiguredHost(String sourceUrl) {
        URI source = parseUri(sourceUrl);
        if (!sameEndpoint(source, configuredCosHost)
                || source.getRawUserInfo() != null || source.getRawQuery() != null || source.getRawFragment() != null) {
            throw unavailable();
        }
        String path = source.getPath();
        if (path == null || !path.startsWith("/")) {
            throw unavailable();
        }
        String key = path.substring(1);
        if (key.isBlank() || Arrays.stream(key.split("/", -1))
                .anyMatch(segment -> segment.isEmpty() || segment.equals(".") || segment.equals("..")
                        || segment.indexOf('\\') >= 0)
                || key.chars().anyMatch(Character::isISOControl)) {
            throw unavailable();
        }
        return key;
    }

    private static URI parseConfiguredHost(String host) {
        URI parsed = parseUri(host);
        if (parsed.getHost() == null || parsed.getRawUserInfo() != null || parsed.getRawQuery() != null
                || parsed.getRawFragment() != null || hasText(parsed.getPath()) && !"/".equals(parsed.getPath())) {
            throw new IllegalArgumentException("cos.client.host must be an origin URI");
        }
        return parsed;
    }

    private static URI parseUri(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException | NullPointerException exception) {
            // URI 的异常文本会回显输入地址，不能作为异常 cause 向上扩散。
            throw unavailable();
        }
    }

    private static boolean sameEndpoint(URI left, URI right) {
        return equalsIgnoreCase(left.getScheme(), right.getScheme())
                && equalsIgnoreCase(left.getHost(), right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443
                : "http".equalsIgnoreCase(uri.getScheme()) ? 80 : -1;
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static Instant resourceVersion(Date updateTime) {
        if (updateTime == null) {
            throw unavailable();
        }
        return updateTime.toInstant();
    }

    private static String mimeTypeFor(String objectKey) {
        int extensionIndex = objectKey.lastIndexOf('.');
        String extension = extensionIndex < 0 ? "" : objectKey.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> throw new VisionContentException("VISION_UNSUPPORTED_IMAGE_FORMAT", "图片格式不支持视觉营养");
        };
    }

    private static void validateMimeSignature(String mimeType, byte[] bytes) {
        boolean matches = switch (mimeType) {
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
            case "image/png" -> bytes.length >= 8
                    && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                    && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
        if (!matches) {
            throw new VisionContentException("VISION_UNSUPPORTED_IMAGE_FORMAT", "图片格式不支持视觉营养");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static VisionContentException unavailable() {
        return new VisionContentException("VISION_IMAGE_UNAVAILABLE", "图片内容无法用于视觉营养");
    }

    private static BusinessException stateChanged() {
        return new BusinessException(ErrorCode.NO_AUTH_ERROR, "图片状态已变化或无权访问");
    }

    /**
     * 只把资源地址变成短生命周期内用于比对的摘要，绝不将地址或对象键带出该 Provider。
     */
    private static String resourceBinding(String sourceUrl) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(sourceUrl.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static byte[] nonEmpty(byte[] bytes) {
        if (bytes.length == 0) {
            throw unavailable();
        }
        return bytes;
    }

    private record PictureSnapshot(long pictureId, String sourceUrl, Instant resourceVersion) {
    }
}
