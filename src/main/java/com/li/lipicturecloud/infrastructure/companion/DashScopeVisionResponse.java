package com.li.lipicturecloud.infrastructure.companion;

import com.fasterxml.jackson.databind.JsonNode;
import com.li.lipicturecloud.application.companion.VisionProviderException;

/**
 * 只从 OpenAI 兼容外层响应中摘取一个 JSON 字符串；不保留响应原文。
 */
final class DashScopeVisionResponse {

    private DashScopeVisionResponse() {
    }

    static String requiredContent(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw invalid();
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() != 1) {
            throw invalid();
        }
        JsonNode content = choices.get(0).path("message").path("content");
        if (!content.isTextual() || content.textValue().isBlank()) {
            throw invalid();
        }
        return content.textValue();
    }

    static VisionProviderException invalid() {
        return new VisionProviderException("VISION_INVALID_RESPONSE", "视觉服务暂不可用");
    }
}
