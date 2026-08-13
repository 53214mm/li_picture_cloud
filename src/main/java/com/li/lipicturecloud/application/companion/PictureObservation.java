package com.li.lipicturecloud.application.companion;

import java.util.Locale;

/**
 * 一张已授权图片的受控观察结果。
 *
 * <p>这里不携带 URL、原始描述或标签文本，避免伙伴领域和未来模型 Adapter
 * 无意间扩散用户图片内容。视觉模型阶段可以新增结构化线索，但仍需遵守最小披露原则。</p>
 */
public record PictureObservation(
        long pictureId,
        boolean hasDescription,
        boolean hasCategory,
        Integer width,
        Integer height,
        Long sizeBytes,
        String format) {

    public PictureObservation {
        if (pictureId <= 0) {
            throw new IllegalArgumentException("pictureId must be positive");
        }
        boolean hasWidth = width != null;
        boolean hasHeight = height != null;
        if (hasWidth != hasHeight || hasWidth && (width <= 0 || height <= 0)) {
            throw new IllegalArgumentException("width and height must be positive and appear together");
        }
        if (sizeBytes != null && sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
        format = normalizeFormat(format);
    }

    public boolean hasDimensions() {
        return width != null;
    }

    private static String normalizeFormat(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 20) {
            throw new IllegalArgumentException("format is too long");
        }
        return normalized;
    }
}
