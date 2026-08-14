package com.li.lipicturecloud.application.companion;

import java.util.Objects;

/**
 * 视觉观察候选 + 本次真实调用来源（供应商/模型/提示词版本/结果结构版本）。
 * 来源随结果一起返回，避免并发调用下共享 Provider 的"最后一次调用"状态错乱。
 */
public record VisualObservationResult(
        VisualObservationCandidate candidate,
        String providerCode,
        String modelCode,
        String promptVersion,
        String resultSchemaVersion) {

    public VisualObservationResult {
        Objects.requireNonNull(candidate, "candidate");
        requireCode(providerCode, "providerCode");
        requireCode(modelCode, "modelCode");
        requireCode(promptVersion, "promptVersion");
        requireCode(resultSchemaVersion, "resultSchemaVersion");
    }

    private static void requireCode(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
