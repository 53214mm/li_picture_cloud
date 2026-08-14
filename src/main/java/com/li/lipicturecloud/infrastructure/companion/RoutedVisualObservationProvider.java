package com.li.lipicturecloud.infrastructure.companion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.ModelRouteDecision;
import com.li.lipicturecloud.application.airuntime.ModelUsageService;
import com.li.lipicturecloud.application.airuntime.VisionRouter;
import com.li.lipicturecloud.application.companion.AuthorizedPictureContent;
import com.li.lipicturecloud.application.companion.VisualObservationProvider;
import com.li.lipicturecloud.application.companion.VisualObservationResult;
import com.li.lipicturecloud.application.companion.VisionProviderException;
import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Objects;

/**
 * 经网关路由的视觉观察 Provider：平台默认走既有 DashScope 路径（行为不变），
 * 用户 BYOK 连接走同一 OpenAI 兼容客户端（按调用实例化，凭据只在本次调用内存中）。
 *
 * <p>BYOK 失败只抛安全错误码，绝不静默切换平台钱包；使用记录失败不得掩盖视觉结果。</p>
 */
public class RoutedVisualObservationProvider implements VisualObservationProvider {

    private static final Logger log = LoggerFactory.getLogger(RoutedVisualObservationProvider.class);

    private final VisionRouter visionRouter;
    private final ModelUsageService usageService;
    private final DashScopeVisionClient platform;
    private final RestClient byokRestClient;
    private final ObjectMapper objectMapper;

    public RoutedVisualObservationProvider(VisionRouter visionRouter,
                                           ModelUsageService usageService,
                                           DashScopeVisionClient platform,
                                           RestClient byokRestClient,
                                           ObjectMapper objectMapper) {
        this.visionRouter = Objects.requireNonNull(visionRouter, "visionRouter");
        this.usageService = Objects.requireNonNull(usageService, "usageService");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.byokRestClient = Objects.requireNonNull(byokRestClient, "byokRestClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public VisualObservationResult observe(AuthorizedPictureContent content, long subjectId) {
        Objects.requireNonNull(content, "content");
        ModelRouteDecision route = visionRouter.decide(subjectId);
        if (route.isByok()) {
            return byokObserve(route, content, subjectId);
        }
        return platformObserve(content, subjectId);
    }

    private VisualObservationResult platformObserve(AuthorizedPictureContent content, long subjectId) {
        try {
            VisualObservationResult result = platform.observe(content, subjectId);
            recordSuccess(subjectId, null, ModelProvider.DASHSCOPE, result.modelCode());
            return result;
        } catch (VisionProviderException failure) {
            recordFailure(subjectId, null, ModelProvider.DASHSCOPE, platform.modelCode(),
                    failure.safeCode());
            throw failure;
        }
    }

    private VisualObservationResult byokObserve(ModelRouteDecision route,
                                                AuthorizedPictureContent content, long subjectId) {
        DashScopeVisionClient byok = new DashScopeVisionClient(byokRestClient, objectMapper,
                URI.create(route.connection().endpointUri().toString()
                        .replaceFirst("/+$", "") + "/chat/completions"),
                route.connection().provider().name(), route.connection().modelCode(),
                route.apiKey());
        try {
            VisualObservationResult result = byok.observe(content, subjectId);
            recordSuccess(subjectId, route.connection().id(), route.connection().provider(),
                    result.modelCode());
            return result;
        } catch (VisionProviderException failure) {
            recordFailure(subjectId, route.connection().id(), route.connection().provider(),
                    route.connection().modelCode(), failure.safeCode());
            throw failure;
        }
    }

    private void recordSuccess(long subjectId, Long connectionId, ModelProvider provider,
                               String modelCode) {
        try {
            usageService.recordSuccess(subjectId, ModelTask.VISION_UNDERSTANDING, connectionId,
                    provider, modelCode, connectionId == null ? CostSource.PLATFORM : CostSource.BYOK);
        } catch (RuntimeException recordFailure) {
            log.warn("companion_vision_usage_record_failed subjectId={}", subjectId);
        }
    }

    private void recordFailure(long subjectId, Long connectionId, ModelProvider provider,
                               String modelCode, String safeErrorCode) {
        try {
            usageService.recordFailure(subjectId, ModelTask.VISION_UNDERSTANDING, connectionId,
                    provider, modelCode, connectionId == null ? CostSource.PLATFORM : CostSource.BYOK,
                    safeErrorCode);
        } catch (RuntimeException recordFailure) {
            log.warn("companion_vision_usage_record_failed subjectId={} code={}",
                    subjectId, safeErrorCode);
        }
    }
}
