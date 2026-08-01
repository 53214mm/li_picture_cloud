package com.li.lipicturecloud.AI.tools;

import com.li.lipicturecloud.AI.app.PicCloudApp;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AiToolPolicyTest {

    @Test
    void tellsTheModelNotToSaveMcpGenerationTwice() throws NoSuchMethodException {
        Tool saveTool = AIGenerationTool.class
                .getMethod("saveToMySpace", String.class, String.class)
                .getAnnotation(Tool.class);
        String systemPrompt = (String) ReflectionTestUtils.getField(PicCloudApp.class, "SYSTEM_PROMPT");

        assertThat(saveTool.description())
                .contains("用户明确提供")
                .doesNotContain("MCP 生成图片后调用");
        assertThat(systemPrompt)
                .contains("MCP 生图结果由后端自动保存，不要再次调用 saveToMySpace");
    }
}
