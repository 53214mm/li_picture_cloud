package com.li.lipicturecloud.application.companion;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 视觉模型返回的、尚未进入伙伴平衡规则的结构化候选。
 */
public record VisualObservationCandidate(
        Mood mood,
        int sceneComplexity,
        int energy,
        boolean socialPresence,
        int motionPotential,
        int creativity,
        BigDecimal confidence) {

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
    }

    private static void requireRange(String name, int value) {
        if (value < 0 || value > 4) {
            throw new IllegalArgumentException(name + " must be between 0 and 4");
        }
    }

    public enum Mood {
        JOYFUL,
        CALM,
        NEUTRAL,
        MELANCHOLIC,
        TENSE
    }
}
