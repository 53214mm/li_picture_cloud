package com.li.lipicturecloud.AI.tools;

import cn.hutool.core.util.StrUtil;
import com.li.lipicturecloud.AI.common.UserContextHolder;
import com.li.lipicturecloud.model.dto.picture.PictureUploadRequest;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.vo.PictureVO;
import com.li.lipicturecloud.service.PictureService;
import com.li.lipicturecloud.service.SpaceService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * AI 空间集成工具 —— 将 MCP 生成的图片保存到用户私有空间。
 * <p>
 * MCP 生成工具（generate_image / generate_video 等）由 PicAgent 直接注入 ToolCallbackProvider，不在此处包装。
 */
@Slf4j
@Component
public class AIGenerationTool {

    @Resource
    private PictureService pictureService;
    @Resource
    private SpaceService spaceService;

    @Tool(description = "将图片 URL 下载并上传到当前用户的私有空间。MCP 生成图片后调用此方法保存。")
    public String saveToMySpace(
            @ToolParam(description = "要保存的图片 URL") String imageUrl,
            @ToolParam(description = "图片名称（可选）") String name) {

        User currentUser = UserContextHolder.get();
        if (currentUser == null) {
            return "无法获取用户信息，请登录后再试。";
        }
        if (StrUtil.isBlank(imageUrl)
                || (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://"))) {
            return "无效的图片地址";
        }
        try {
            var spaceList = spaceService.lambdaQuery()
                    .eq(com.li.lipicturecloud.model.entity.Space::getUserId, currentUser.getId())
                    .list();
            if (spaceList.isEmpty()) {
                return "还没有私有空间，请先在平台创建空间。";
            }
            Long spaceId = spaceList.get(0).getId();

            PictureUploadRequest uploadReq = new PictureUploadRequest();
            uploadReq.setFileUrl(imageUrl);
            uploadReq.setSpaceId(spaceId);
            if (StrUtil.isNotBlank(name)) {
                uploadReq.setPicName(name);
            }

            PictureVO result = pictureService.uploadPicture(imageUrl, uploadReq, currentUser);
            return String.format("已保存到空间「%s」，图片 ID: %s", spaceList.get(0).getSpaceName(), result.getId());
        } catch (Exception e) {
            log.error("保存图片到空间失败", e);
            return "保存失败，请稍后重试。";
        }
    }
}
