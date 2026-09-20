package com.li.lipicturecloud.infrastructure.companion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.companion.AuthorizedPictureContent;
import com.li.lipicturecloud.application.companion.VisualObservationCandidate;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 人工选择运行的真实 DashScope 冒烟测试。
 *
 * <p>默认和 CI 都会跳过。测试只发送仓库内的公开马赛克素材，不打印请求、模型原文或 API key；
 * 它只验证真实服务仍能满足结构化候选契约。</p>
 */
class DashScopeVisionLiveSmokeTest {

    @Test
    void authorizedOperatorCanVerifyTheLiveStructuredVisionContract() throws Exception {
        String enabled = System.getenv("COMPANION_VISION_LIVE_TEST");
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        assumeTrue("true".equalsIgnoreCase(enabled) && apiKey != null && !apiKey.isBlank(),
                "live vision smoke is opt-in and requires an operator-supplied key");

        Path fixture = Path.of("li-picture-cloud-frontend", "public", "images", "mosaic", "abstract.jpg");
        byte[] bytes = Files.readAllBytes(fixture);
        assertThat(bytes.length).isLessThanOrEqualTo((int) DataSize.ofMegabytes(8).toBytes());
        CompanionFeatureProperties properties = new CompanionFeatureProperties();
        properties.setVisionEndpoint(URI.create(environmentOrDefault("COMPANION_VISION_ENDPOINT",
                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions")));
        properties.setVisionProvider("dashscope");
        properties.setVisionModel(environmentOrDefault("COMPANION_VISION_MODEL", "qwen3.6-flash"));
        properties.setVisionApiKey(apiKey);
        properties.setVisionTimeout(Duration.ofSeconds(30));
        properties.setVisionMaxBytes(DataSize.ofMegabytes(8));
        DashScopeVisionClient client = DashScopeVisionClient.fromProperties(new ObjectMapper(), properties);

        VisualObservationCandidate candidate = client.observe(new AuthorizedPictureContent(
                1L, Instant.EPOCH, "image/jpeg", "c".repeat(64), bytes), 7L).candidate();

        assertThat(candidate.confidence()).isBetween(java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE);
        assertThat(candidate.sceneComplexity()).isBetween(0, 4);
        assertThat(candidate.energy()).isBetween(0, 4);
        assertThat(candidate.motionPotential()).isBetween(0, 4);
        assertThat(candidate.creativity()).isBetween(0, 4);
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
