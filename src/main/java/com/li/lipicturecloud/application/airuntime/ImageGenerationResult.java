package com.li.lipicturecloud.application.airuntime;

import java.net.URI;

/**
 * 一次图片创作的结果：供应商返回 URL 或内联 base64（至少其一）。
 * URL 是供应商的临时资源地址，不会写入伙伴内容或日志。
 */
public record ImageGenerationResult(URI imageUrl, String base64Image) {

    public ImageGenerationResult {
        if (imageUrl == null && (base64Image == null || base64Image.isBlank())) {
            throw new IllegalArgumentException("image result must carry a url or inline image");
        }
        if (base64Image != null && base64Image.length() > 16 * 1024 * 1024) {
            throw new IllegalArgumentException("inline image exceeds the 16 MiB safety limit");
        }
    }
}
