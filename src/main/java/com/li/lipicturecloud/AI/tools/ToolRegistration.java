package com.li.lipicturecloud.AI.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工具注册配置类，将所有工具注册为 Spring Bean 供智能体调用
 */
@Configuration
public class ToolRegistration {

    @Autowired
    private SearchTool searchTool;

    @Autowired
    private AIGenerationTool aiGenerationTool;
    @Autowired
    private PictureManageTool pictureManageTool;
    @Autowired
    private PictureAnalysisTool pictureAnalysisTool;

    @Bean
    public ToolCallback[] allTools() {
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        TerminateTool terminateTool = new TerminateTool();
        return ToolCallbacks.from(
            searchTool,
            webScrapingTool,
            terminateTool,
            aiGenerationTool,
            // 图片管理与分析工具
            pictureManageTool,
            pictureAnalysisTool
        );
    }
}
