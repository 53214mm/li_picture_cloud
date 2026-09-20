package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRule;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRuleRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 视觉理解任务路由器：用户显式绑定连接则走 BYOK；未绑定或未配置路由则走平台。
 *
 * <p>与语言路由同一红线：BYOK 规则存在但连接不可用或能力不足时必须大声失败，
 * 绝不静默回退到平台钱包扣费。视觉能力以最近一次连接测试生成的能力画像为准，
 * 未知能力一律按不支持处理。</p>
 */
@Service
public class VisionRouter {

    private final TaskRoutingRuleRepository routingRepository;
    private final ByokConnectionResolver byokResolver;
    private final ModelCapabilityProfileService profileService;

    public VisionRouter(TaskRoutingRuleRepository routingRepository,
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
                ModelTask.VISION_UNDERSTANDING);
        if (rule.isEmpty() || rule.get().connectionId() == null) {
            // 未配置规则或用户显式选择平台：平台视觉路径。
            return ModelRouteDecision.platform();
        }

        ModelRouteDecision decision = byokResolver.resolveByok(subjectId,
                rule.get().connectionId(), "视觉");
        com.li.lipicturecloud.domain.airuntime.ModelCapabilityProfile profile =
                profileService.findLatest(decision.connection().id())
                        .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR,
                                "视觉任务路由的连接尚未生成能力画像，请先测试连接"));
        if (!profile.vision()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "视觉任务路由的连接模型不支持视觉理解，请修复或清除路由规则");
        }
        return decision;
    }
}
