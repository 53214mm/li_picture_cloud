package com.li.lipicturecloud.application.companion;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 视觉模型返回的、尚未进入伙伴平衡规则的结构化候选。
 *
 * <p>数值字段只提供观察依据，最终经验和属性仍由服务端平衡规则决定；
 * {@code companionMessage} 是允许写入成长档案的短篇伙伴独白，必须先通过纯文本边界校验。</p>
 */
public record VisualObservationCandidate(
        Mood mood,
        int sceneComplexity,
        int energy,
        boolean socialPresence,
        int motionPotential,
        int creativity,
        BigDecimal confidence,
        String companionMessage) {

    public static final int MIN_MESSAGE_CODE_POINTS = 20;
    public static final int MAX_MESSAGE_CODE_POINTS = 240;

    public VisualObservationCandidate {
        Objects.requireNonNull(mood, "mood");
        requireRange("sceneComplexity", sceneComplexity);
        requireRange("energy", energy);
        requireRange("motionPotential", motionPotential);
        requireRange("creativity", creativity);
        Objects.requireNonNull(confidence, "confidence");
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        Objects.requireNonNull(companionMessage, "companionMessage");
        companionMessage = companionMessage.strip();
        int messageLength = companionMessage.codePointCount(0, companionMessage.length());
        if (messageLength < MIN_MESSAGE_CODE_POINTS || messageLength > MAX_MESSAGE_CODE_POINTS) {
            throw new IllegalArgumentException("companionMessage must be between 20 and 240 characters");
        }
        if (companionMessage.codePoints().anyMatch(Character::isISOControl)
                || containsExternalLink(companionMessage)) {
            throw new IllegalArgumentException("companionMessage must be safe plain text");
        }
    }

    private static void requireRange(String name, int value) {
        if (value < 0 || value > 4) {
            throw new IllegalArgumentException(name + " must be between 0 and 4");
        }
    }

    private static boolean containsExternalLink(String value) {
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("http://") || normalized.contains("https://") || normalized.contains("www.");
    }

    public enum Mood {
        JOYFUL,
        CALM,
        NEUTRAL,
        MELANCHOLIC,
        TENSE
    }
}
