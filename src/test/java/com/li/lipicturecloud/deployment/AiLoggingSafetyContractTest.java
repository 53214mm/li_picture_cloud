package com.li.lipicturecloud.deployment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AiLoggingSafetyContractTest {

    @Test
    void productionAiPathsLogMetadataInsteadOfPromptsResponsesArgumentsOrResults() throws IOException {
        String advisor = read("src/main/java/com/li/lipicturecloud/AI/advisor/MyLoggerAdvisor.java");
        String agent = read("src/main/java/com/li/lipicturecloud/AI/agent/ToolCallAgent.java");
        String mcp = read("src/main/java/com/li/lipicturecloud/AI/config/RefreshableMcpToolProvider.java");

        assertThat(advisor)
                .contains("ai_request_started", "ai_response_received")
                .doesNotContain("request.context()", "ai-response: {}", ".getText()");
        assertThat(agent)
                .contains("agent_think_completed", "agent_tool_execution_completed")
                .doesNotContain("log.info(toolCallInfo)", "log.info(results)", "e.printStackTrace()",
                        "思考：\" + result", "e.getMessage()");
        assertThat(mcp)
                .contains("mcp_generation_submitted", "exceptionType={}")
                .doesNotContain("原始返回", "e.getMessage()");
    }

    private String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
