package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRule;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRuleRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 图片创作任务路由器。首个图片 Adapter 目标 gpt-image-2（设计默认）；
 * 平台图片创作账本未上线前，平台路由由调用方大声失败，
 * 用户 BYOK 连接必须通过 imageGeneration 能力画像门禁。
 */
@Service
public class ImageRouter {

    private final TaskRoutingRuleRepository routingRepository;
    private final ByokConnectionResolver byokResolver;
    private final ModelCapabilityProfileService profileService;

    public ImageRouter(TaskRoutingRuleRepository routingRepository,
                       ByokConnectionResolver byokResolver,
                       ModelCapabilityProfileService profileService) {
        this.routingRepository = routingRepository;
        this.byokResolver = byokResolver;
        this.profileService = profileService;
    }

    public ModelRouteDecision decide(long subjectId) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
        Optional<TaskRoutingRule> rule = routingRepository.findBySubjectAndTask(subjectId,
                ModelTask.IMAGE_CREATION);
        if (rule.isEmpty() || rule.get().connectionId() == null) {
            // 未配置规则或用户显式选择平台：平台图片创作路径（账本未上线前调用方大声失败）。
            return ModelRouteDecision.platform();
        }

        ModelRouteDecision decision = byokResolver.resolveByok(subjectId,
                rule.get().connectionId(), "图片创作");
        com.li.lipicturecloud.domain.airuntime.ModelCapabilityProfile profile =
                profileService.findLatest(decision.connection().id())
                        .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR,
                                "图片创作任务路由的连接尚未生成能力画像，请先测试连接"));
        if (!profile.imageGeneration()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "图片创作任务路由的连接模型不支持图像生成，请修复或清除路由规则");
        }
        return decision;
    }
}
