package com.li.lipicturecloud.AI;

import java.util.Locale;

/** Classifies only explicit image-generation requests for progress messaging. */
public final class AiRequestIntent {

    private AiRequestIntent() {
    }

    public static boolean isImageGenerationRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("generate_image")
                || normalized.contains("generate image")
                || normalized.contains("生图")
                || normalized.contains("文生图")
                || normalized.contains("以图生图")
                || normalized.matches(".*生成.{0,12}(图|图片|图像|画面|海报|头像|插画).*")
                || normalized.matches(".*帮我(画|生成).{0,12}(图|图片|图像|画面|海报|头像|插画).*");
    }
}
