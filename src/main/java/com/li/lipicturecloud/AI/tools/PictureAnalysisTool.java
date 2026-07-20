package com.li.lipicturecloud.AI.tools;

import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.service.PictureService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 图片分析与优化工具 —— 提供格式分析、压缩建议、尺寸优化等能力
 * <p>
 * Agent 可通过此工具为用户提供专业的图片优化建议。
 */
@Slf4j
@Component
public class PictureAnalysisTool {

    @Resource
    private PictureService pictureService;

    /**
     * 分析指定图片并提供优化建议
     */
    @Tool(description = "分析 LiPictureCloud 平台中某张图片的格式、尺寸和质量，给出专业的优化建议（压缩、裁剪、格式转换等）。需要提供图片 ID。")
    public String analyzePicture(
            @ToolParam(description = "要分析的图片 ID") Long pictureId) {

        if (pictureId == null) return "请提供图片 ID";
        try {
            Picture pic = pictureService.getById(pictureId);
            if (pic == null) return "未找到该图片";
            // 仅允许分析公开图库中已过审的图片
            if (pic.getSpaceId() != null || pic.getReviewStatus() == null
                    || pic.getReviewStatus() != 1) {
                return "该图片不可公开访问，无法分析";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== 图片分析报告 ===\n\n");

        // 基本信息
        sb.append(String.format("名称: %s\n", pic.getName()));
        sb.append(String.format("格式: %s\n", pic.getPicFormat()));
        sb.append(String.format("原始尺寸: %d × %d px\n", pic.getPicWidth(), pic.getPicHeight()));
        sb.append(String.format("文件大小: %s\n", formatSize(pic.getPicSize())));
        sb.append(String.format("宽高比: %.2f (%s)\n", pic.getPicScale(), getAspectRatioDesc(pic.getPicScale())));
        sb.append(String.format("分辨率: %d 万像素\n", (pic.getPicWidth() * pic.getPicHeight()) / 10000));
        sb.append("\n");

        // 格式分析
        sb.append("【格式分析】\n");
        String fmt = pic.getPicFormat() != null ? pic.getPicFormat().toLowerCase() : "unknown";
        switch (fmt) {
            case "jpg", "jpeg" ->
                    sb.append("当前为 JPEG 格式。建议转为 WebP 格式，通常可减小 40%-60% 体积且画质无明显损失。\n");
            case "png" ->
                    sb.append("当前为 PNG 格式。对于照片类图片建议转 WebP（更小体积），UI截图类建议保持 PNG（保真度高）。\n");
            case "webp" ->
                    sb.append("当前已是 WebP 格式 ✓，这是推荐的现代图片格式，兼顾画质与体积。\n");
            default -> sb.append("建议转为 WebP 或 AVIF 格式以获得更好的压缩率。\n");
        }

        // 尺寸分析
        sb.append("\n【尺寸分析】\n");
        long pixels = (long) pic.getPicWidth() * pic.getPicHeight();
        if (pixels > 8_000_000) {
            sb.append("图片分辨率很高（>800万像素）。如用于网页展示，建议缩放到 1920px 宽以内，可大幅减小文件体积。\n");
        } else if (pixels > 2_000_000) {
            sb.append("图片分辨率适中。如需进一步优化加载速度，可考虑缩放到 1200-1600px 宽。\n");
        } else {
            sb.append("图片尺寸合理 ✓，适合网页展示。\n");
        }

        // 压缩潜力估算
        sb.append("\n【压缩建议】\n");
        Long size = pic.getPicSize();
        if (size != null && size > 500_000 && !"webp".equals(fmt)) {
            sb.append(String.format("当前 %s 格式文件 %s，预计转 WebP 后可降至约 %s（节省约 %d%%）。\n",
                    fmt.toUpperCase(),
                    formatSize(size),
                    formatSize(size * 4 / 10),
                    60));
        } else if (size != null && size > 2_000_000) {
            sb.append("文件较大，建议适当降低分辨率或提高压缩率（JPG质量 80-85% 通常肉眼无损）。\n");
        } else {
            sb.append("文件大小合理 ✓，无需额外压缩。\n");
        }

            return sb.toString();
        } catch (Exception e) {
            log.error("分析图片失败, pictureId={}", pictureId, e);
            return "分析图片时出现错误，请稍后重试。";
        }
    }

    /**
     * 通用图片格式知识问答
     */
    @Tool(description = "获取图片格式相关的专业知识：包括常见图片格式对比（JPEG/PNG/WebP/AVIF/GIF）、适用场景、压缩特性等。")
    public String getFormatGuide(
            @ToolParam(description = "想了解的格式名称（可选），如'webp''png''avif'，不填则返回所有格式对比") String format) {

        if (format == null || format.isBlank()) {
            return """
                    === 常见图片格式对比 ===

                    JPEG: 有损压缩，适合照片，不支持透明，文件小 → 推荐用于摄影图片
                    PNG:  无损压缩，支持透明，文件大 → 推荐用于 UI/Logo/截图
                    WebP: 有损+无损，支持透明+动画，文件比JPEG小30% → 现代网页首选
                    AVIF: 新一代格式，压缩率比WebP更高，支持HDR → 渐进替代WebP
                    GIF:  256色，支持动画，文件大 → 仅用于简单动画
                    SVG:  矢量格式，无限缩放 → 推荐用于图标/插图
                    """;
        }

        return switch (format.toLowerCase()) {
            case "webp" -> "WebP 是 Google 推出的现代图片格式，支持有损和无损压缩、透明通道和动画。相比 JPEG 体积减小 25%-35%，画质相当。主流浏览器均已支持。";
            case "avif" -> "AVIF 基于 AV1 编码，是 WebP 的继任者。压缩率比 WebP 高 20%-30%，支持 HDR 和广色域。Chrome 85+ 和 Firefox 93+ 支持。";
            case "png" -> "PNG 是无损压缩格式，支持全透明通道（RGBA）。适合需要精确像素保真的场景（UI截图、Logo），但文件体积通常比 JPEG 大 3-5 倍。";
            case "jpeg", "jpg" -> "JPEG 是最广泛使用的有损压缩格式，适合照片和复杂渐变图像。不支持透明。推荐质量 80-85% 可肉眼无损。";
            case "gif" -> "GIF 仅支持 256 色，但兼容性极好。适合简单动画和低色数图形，不适合照片。建议用 WebP 动画或 MP4 替代。";
            default -> "未找到格式「" + format + "」的详细资料。常见格式包括：webp, avif, png, jpeg, gif。";
        };
    }

    private String formatSize(Long bytes) {
        if (bytes == null) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / 1048576.0);
    }

    private String getAspectRatioDesc(Double scale) {
        if (scale == null) return "未知";
        if (Math.abs(scale - 1.0) < 0.05) return "正方形";
        if (Math.abs(scale - 4.0 / 3.0) < 0.05) return "4:3 标准";
        if (Math.abs(scale - 16.0 / 9.0) < 0.05) return "16:9 宽屏";
        if (Math.abs(scale - 3.0 / 2.0) < 0.05) return "3:2 摄影";
        if (scale > 1.5) return "横向（宽屏）";
        return "竖向（竖屏）";
    }
}
