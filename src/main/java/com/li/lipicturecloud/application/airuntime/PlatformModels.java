package com.li.lipicturecloud.application.airuntime;

/**
 * 平台自带模型的编码，用于使用记录与回执标注真实来源。
 *
 * <p>必须与 application.yaml 中 {@code spring.ai.dashscope.chat.options.model} 保持一致。</p>
 */
public final class PlatformModels {

    /** 平台对话模型（百炼千问）。 */
    public static final String CHAT = "qwen3.8-max";

    private PlatformModels() {
    }
}
