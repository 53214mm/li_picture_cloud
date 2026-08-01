package com.li.lipicturecloud.AI.tools;

import com.li.lipicturecloud.AI.service.AiPictureSaveService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ToolRegistrationTest {

    @Test
    void exposesBusinessToolsWithoutGeneralServerOperationTools() {
        ToolRegistration registration = new ToolRegistration();
        ReflectionTestUtils.setField(registration, "searchTool", new SearchTool());
        ReflectionTestUtils.setField(registration, "aiGenerationTool",
                new AIGenerationTool(mock(AiPictureSaveService.class)));
        ReflectionTestUtils.setField(registration, "pictureManageTool", new PictureManageTool());
        ReflectionTestUtils.setField(registration, "pictureAnalysisTool", new PictureAnalysisTool());

        Set<String> toolNames = Arrays.stream(registration.allTools())
                .map(ToolCallback::getToolDefinition)
                .map(definition -> definition.name())
                .collect(Collectors.toSet());

        assertThat(toolNames).contains(
                "saveToMySpace", "getPictureStats", "analyzePicture", "getFormatGuide");
        assertThat(toolNames).doesNotContain(
                "executeCommandSafe", "readFile", "writeFile", "downloadResource",
                "createPdf", "extractText", "getPageCount", "mergePdfs", "splitPdf");
    }
}
