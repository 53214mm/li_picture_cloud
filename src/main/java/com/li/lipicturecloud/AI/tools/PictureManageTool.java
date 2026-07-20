package com.li.lipicturecloud.AI.tools;

import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.service.PictureService;
import com.li.lipicturecloud.service.SpaceService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 图片管理工具 —— 封装本系统的图片查询、搜索、统计能力。
 * <p>
 * 安全限制：仅返回公开图库中已过审的图片（reviewStatus=1, spaceId IS NULL），
 * 不暴露私有空间数据。
 */
@Slf4j
@Component
public class PictureManageTool {

    @Resource
    private PictureService pictureService;
    @Resource
    private SpaceService spaceService;

    /**
     * 获取公开图片统计
     */
    @Tool(description = "查询 LiPictureCloud 平台公开图库的图片统计信息。")
    public String getPictureStats() {
        try {
            long total = pictureService.lambdaQuery()
                    .isNull(Picture::getSpaceId).eq(Picture::getReviewStatus, 1).count();
            return String.format("平台公开图库 | 已过审图片: %d 张", total);
        } catch (Exception e) {
            log.error("查询图片统计失败", e);
            return "查询统计信息时出现错误，请稍后重试。";
        }
    }

    /**
     * 按关键词搜索公开图片（含缩略图 URL，AI 可渲染为图片展示）
     */
    @Tool(description = "在 LiPictureCloud 公开图库中按关键词搜索已过审的图片。结果含缩略图URL，请用 Markdown 图片语法展示给用户。搜索范围包括图片名称和简介。")
    public String searchPictures(
            @ToolParam(description = "搜索关键词（必填），如'风景''城市''猫咪'") String keyword,
            @ToolParam(description = "分类筛选（可选），如'风景''人物''建筑'") String category,
            @ToolParam(description = "返回数量（可选），默认 5，最大 10") Integer limit) {

        int count = limit != null ? Math.min(limit, 10) : 5;
        try {
            List<Picture> list = pictureService.lambdaQuery()
                    .isNull(Picture::getSpaceId)
                    .eq(Picture::getReviewStatus, 1)
                    .and(keyword != null, w -> w.like(Picture::getName, keyword)
                            .or().like(Picture::getIntroduction, keyword))
                    .eq(category != null && !category.isEmpty(), Picture::getCategory, category)
                    .orderByDesc(Picture::getCreateTime)
                    .last("LIMIT " + count)
                    .list();

            if (list.isEmpty()) {
                return "未在公开图库中找到匹配「" + keyword + "」的图片。建议尝试其他关键词。";
            }
            return list.stream()
                    .map(p -> {
                        String thumb = p.getThumbnailUrl() != null ? p.getThumbnailUrl() : p.getUrl();
                        return String.format("![%s](%s)\n**[%s]** %s | %s | %dx%d | %s",
                                p.getName(), thumb,
                                p.getId(), p.getName(), p.getPicFormat(),
                                p.getPicWidth(), p.getPicHeight(), formatSize(p.getPicSize()));
                    })
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.error("搜索图片失败, keyword={}, category={}", keyword, category, e);
            return "搜索图片时出现错误，请稍后重试。";
        }
    }

    /**
     * 查询公开图片的详细信息
     */
    @Tool(description = "根据图片 ID 查询公开图库中某张已过审图片的详细信息，包括缩略图 URL、名称、格式、尺寸、分类等。请用 Markdown 展示图片。")
    public String getPictureDetail(
            @ToolParam(description = "图片 ID") Long pictureId) {

        if (pictureId == null) return "请提供图片 ID";
        try {
            Picture pic = pictureService.getById(pictureId);
            if (pic == null) return "未找到该图片";
            if (pic.getSpaceId() != null || pic.getReviewStatus() == null
                    || pic.getReviewStatus() != 1) {
                return "该图片不可公开访问";
            }

            String thumb = pic.getThumbnailUrl() != null ? pic.getThumbnailUrl() : pic.getUrl();
            return String.format("""
                            ![%s](%s)

                            **图片详情**
                            - ID: %s
                            - 名称: %s
                            - 格式: %s | 尺寸: %dx%d
                            - 大小: %s | 宽高比: %.2f
                            - 分类: %s | 标签: %s
                            - 上传时间: %s
                            """,
                    pic.getName(), thumb,
                    pic.getId(), pic.getName(),
                    pic.getPicFormat(), pic.getPicWidth(), pic.getPicHeight(),
                    formatSize(pic.getPicSize()), pic.getPicScale(),
                    pic.getCategory() != null ? pic.getCategory() : "无",
                    pic.getTags() != null ? pic.getTags() : "无",
                    pic.getCreateTime());
        } catch (Exception e) {
            log.error("查询图片详情失败", e);
            return "查询图片时出现错误，请稍后重试。";
        }
    }

    /**
     * 列出最近的公开图片（含缩略图 URL）
     */
    @Tool(description = "获取 LiPictureCloud 公开图库最近上传的已过审图片列表。结果含缩略图URL，请用 Markdown 图片语法展示给用户。")
    public String listRecentPictures(
            @ToolParam(description = "返回数量（可选），默认 5") Integer count) {

        int limit = count != null ? Math.min(count, 10) : 5;
        try {
            List<Picture> list = pictureService.lambdaQuery()
                    .isNull(Picture::getSpaceId)
                    .eq(Picture::getReviewStatus, 1)
                    .orderByDesc(Picture::getCreateTime)
                    .last("LIMIT " + limit)
                    .list();

            if (list.isEmpty()) return "公开图库暂无图片";
            return list.stream()
                    .map(p -> {
                        String thumb = p.getThumbnailUrl() != null ? p.getThumbnailUrl() : p.getUrl();
                        return String.format("![%s](%s)\n**[%s]** %s | %dx%d | %s",
                                p.getName(), thumb,
                                p.getId(), p.getName(),
                                p.getPicWidth(), p.getPicHeight(), formatSize(p.getPicSize()));
                    })
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.error("查询最新图片失败", e);
            return "查询图片列表时出现错误，请稍后重试。";
        }
    }

    private static String formatSize(Long bytes) {
        if (bytes == null) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / 1048576.0);
    }
}
