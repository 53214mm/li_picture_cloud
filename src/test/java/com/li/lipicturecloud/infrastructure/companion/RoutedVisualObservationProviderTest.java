package com.li.lipicturecloud.infrastructure.companion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.ModelRouteDecision;
import com.li.lipicturecloud.application.airuntime.ModelUsageService;
import com.li.lipicturecloud.application.airuntime.VisionRouter;
import com.li.lipicturecloud.application.companion.AuthorizedPictureContent;
import com.li.lipicturecloud.application.companion.VisualObservationCandidate;
import com.li.lipicturecloud.application.companion.VisualObservationResult;
import com.li.lipicturecloud.application.companion.VisionProviderException;
import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 视觉经网关路由：平台默认走既有 DashScope 路径，BYOK 走同一 OpenAI 兼容契约；
 * BYOK 失败绝不静默回退平台，使用记录失败不掩盖视觉结果。
 */
class RoutedVisualObservationProviderTest {

    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");

    private VisionRouter visionRouter;
    private ModelUsageService usageService;
    private DashScopeVisionClient platform;
    private MockRestServiceServer byokServer;
    private RoutedVisualObservationProvider provider;
    private AuthorizedPictureContent content;

    private static final String VALID_CANDIDATE = """
            {"mood":"JOYFUL","sceneComplexity":2,"energy":3,"socialPresence":true,
            "motionPotential":2,"creativity":3,"confidence":0.84,
            "companionMessage":"我像走进了一段被阳光照亮的旅程：明快的色彩和人物间的呼应让我感到热闹，细节也勾起了我的好奇心。"}
            """;

    @BeforeEach
    void setUp() {
        visionRouter = mock(VisionRouter.class);
        usageService = mock(ModelUsageService.class);
        platform = mock(DashScopeVisionClient.class);
        when(platform.modelCode()).thenReturn("qwen3.6-flash");
        RestClient.Builder builder = RestClient.builder();
        byokServer = MockRestServiceServer.bindTo(builder).build();
        provider = new RoutedVisualObservationProvider(visionRouter, usageService, platform,
                builder.build(), new ObjectMapper());
        content = new AuthorizedPictureContent(102L, NOW, "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }

    private static String responseContent(String candidate) {
        try {
            return new ObjectMapper().writeValueAsString(java.util.Map.of(
                    "choices", java.util.List.of(java.util.Map.of(
                            "message", java.util.Map.of("content", candidate)))));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ModelConnection byokConnection() {
        return ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK, "主力",
                URI.create("https://api.deepseek.com/v1"), "deepseek-chat", 5L, true, 1L);
    }

    private VisualObservationResult result(String provider, String model) {
        return new VisualObservationResult(new VisualObservationCandidate(
                VisualObservationCandidate.Mood.JOYFUL, 2, 3, true, 2, 3, new BigDecimal("0.84"),
                "我像走进了一段被阳光照亮的旅程：明快的色彩和人物间的呼应让我感到热闹，细节也勾起了我的好奇心。"),
                provider, model, "companion-vision-v2", "visual-observation-v2");
    }

    @Test
    void platformRouteUsesExistingDashScopePathAndRecordsPlatformUsage() {
        when(visionRouter.decide(7L)).thenReturn(ModelRouteDecision.platform());
        when(platform.observe(content, 7L)).thenReturn(result("dashscope", "qwen3.6-flash"));

        VisualObservationResult observed = provider.observe(content, 7L);

        assertThat(observed.modelCode()).isEqualTo("qwen3.6-flash");
        verify(usageService).recordSuccess(7L, ModelTask.VISION_UNDERSTANDING, null,
                ModelProvider.DASHSCOPE, "qwen3.6-flash", CostSource.PLATFORM);
    }

    @Test
    void platformVisionFailureIsRecordedAndNeverSilentlyDowngraded() {
        when(visionRouter.decide(7L)).thenReturn(ModelRouteDecision.platform());
        VisionProviderException failure = new VisionProviderException("VISION_TIMEOUT", "视觉服务暂不可用");
        when(platform.observe(content, 7L)).thenThrow(failure);

        assertThatThrownBy(() -> provider.observe(content, 7L)).isSameAs(failure);
        verify(usageService).recordFailure(7L, ModelTask.VISION_UNDERSTANDING, null,
                ModelProvider.DASHSCOPE, "qwen3.6-flash", CostSource.PLATFORM, "VISION_TIMEOUT");
    }

    @Test
    void byokRouteCallsTheUserConnectionContractAndRecordsByokUsage() {
        when(visionRouter.decide(7L)).thenReturn(
                ModelRouteDecision.byok(byokConnection(), "sk-secret"));
        byokServer.expect(requestTo("https://api.deepseek.com/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer sk-secret"))
                .andExpect(jsonPath("$.model").value("deepseek-chat"))
                .andRespond(withSuccess(responseContent(VALID_CANDIDATE),
                        org.springframework.http.MediaType.APPLICATION_JSON));

        VisualObservationResult observed = provider.observe(content, 7L);

        assertThat(observed.providerCode()).isEqualTo("DEEPSEEK");
        assertThat(observed.modelCode()).isEqualTo("deepseek-chat");
        verify(usageService).recordSuccess(7L, ModelTask.VISION_UNDERSTANDING, 9L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK);
        byokServer.verify();
    }

    @Test
    void byokFailureIsRecordedWithSafeCodeAndNeverFallsBackToPlatform() {
        when(visionRouter.decide(7L)).thenReturn(
                ModelRouteDecision.byok(byokConnection(), "sk-secret"));
        byokServer.expect(requestTo("https://api.deepseek.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> provider.observe(content, 7L))
                .isInstanceOf(VisionProviderException.class)
                .extracting(error -> ((VisionProviderException) error).safeCode())
                .isEqualTo("VISION_CREDENTIALS");
        verify(usageService).recordFailure(7L, ModelTask.VISION_UNDERSTANDING, 9L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK, "VISION_CREDENTIALS");
        verify(platform, never()).observe(any(), anyLong());
        byokServer.verify();
    }
}
