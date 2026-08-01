package com.li.lipicturecloud.AI.tools;

import com.li.lipicturecloud.AI.common.UserContextHolder;
import com.li.lipicturecloud.AI.service.AiPictureSaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * AI 空间集成工具 —— 将 MCP 生成的图片保存到用户私有空间。
 * <p>
 * MCP 生成工具（generate_image / generate_video 等）由 PicAgent 直接注入 ToolCallbackProvider，不在此处包装。
 */
@Component
@RequiredArgsConstructor
public class AIGenerationTool {

    private final AiPictureSaveService saveService;

    @Tool(description = "将图片 URL 下载并上传到当前用户的私有空间。MCP 生成图片后调用此方法保存。")
    public String saveToMySpace(
            @ToolParam(description = "要保存的图片 URL") String imageUrl,
            @ToolParam(description = "图片名称（可选）") String name) {

        return saveService.save(imageUrl, name, UserContextHolder.get());
    }
}
