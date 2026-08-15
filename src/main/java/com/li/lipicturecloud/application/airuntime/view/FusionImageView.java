package com.li.lipicturecloud.application.airuntime.view;

/**
 * 融合生成结果的预览视图：仅 mime 与字节，不回显任何文本。
 */
public record FusionImageView(String mimeType, byte[] bytes) {
}
