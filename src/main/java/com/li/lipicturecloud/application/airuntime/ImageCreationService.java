package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

/**
 * 图片创作应用服务：路由 → 调用 → 使用记录。
 * 平台图片创作账本未上线前平台路由大声失败；BYOK 失败绝不静默回退。
 */
@Service
public class ImageCreationService {

    private static final Logger log = LoggerFactory.getLogger(ImageCreationService.class);
    private static final int MAX_PROMPT_CODE_POINTS = 2000;
    private static final Set<String> SUPPORTED_SIZES = Set.of(
            "1024x1024", "1536x1024", "1024x1536", "auto");

    private final ImageRouter imageRouter;
    private final ImageModelInvoker imageInvoker;
    private final ModelUsageService usageService;

    public ImageCreationService(ImageRouter imageRouter, ImageModelInvoker imageInvoker,
                                ModelUsageService usageService) {
        this.imageRouter = imageRouter;
        this.imageInvoker = imageInvoker;
        this.usageService = usageService;
    }

    public ImageGenerationResult generate(long subjectId, String prompt, String size) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
        String normalized = validatePrompt(prompt);
        String normalizedSize = validateSize(size);
        ModelRouteDecision route = imageRouter.decide(subjectId);
        if (!route.isByok()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "平台图片创作尚未开放，请在控制中心为图像生成任务绑定用户连接");
        }
        try {
            ImageGenerationResult result = imageInvoker.invoke(route, normalized, normalizedSize);
            recordSuccess(subjectId, route);
            return result;
        } catch (ModelInvocationException failure) {
            recordFailure(subjectId, route, failure.safeErrorCode());
            throw failure;
        }
    }

    private void recordSuccess(long subjectId, ModelRouteDecision route) {
        try {
            usageService.recordSuccess(subjectId, ModelTask.IMAGE_CREATION,
                    route.connection().id(), route.connection().provider(),
                    route.connection().modelCode(), CostSource.BYOK);
        } catch (RuntimeException recordFailure) {
            log.warn("image_creation_usage_record_failed subjectId={}", subjectId);
        }
    }

    private void recordFailure(long subjectId, ModelRouteDecision route, String safeErrorCode) {
        try {
            usageService.recordFailure(subjectId, ModelTask.IMAGE_CREATION,
                    route.connection().id(), route.connection().provider(),
                    route.connection().modelCode(), CostSource.BYOK, safeErrorCode);
        } catch (RuntimeException recordFailure) {
            log.warn("image_creation_usage_record_failed subjectId={} code={}",
                    subjectId, safeErrorCode);
        }
    }

    private static String validatePrompt(String prompt) {
        Objects.requireNonNull(prompt, "prompt");
        String normalized = prompt.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > MAX_PROMPT_CODE_POINTS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "创作描述需为 1-" + MAX_PROMPT_CODE_POINTS + " 字");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创作描述包含不支持的字符");
        }
        return normalized;
    }

    private static String validateSize(String size) {
        Objects.requireNonNull(size, "size");
        if (!SUPPORTED_SIZES.contains(size)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "不支持的图片尺寸，可选：" + String.join("/", SUPPORTED_SIZES));
        }
        return size;
    }
}
