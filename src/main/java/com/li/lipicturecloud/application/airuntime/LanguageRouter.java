package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRule;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRuleRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 语言任务路由器：用户显式绑定连接则走 BYOK；未绑定或未配置路由则走平台。
 *
 * <p>关键约束：一旦存在指向用户连接的 BYOK 规则，连接不可用或能力不足时
 * 必须大声失败，绝不静默回退到平台钱包扣费。</p>
 */
@Service
public class LanguageRouter {

    private final TaskRoutingRuleRepository routingRepository;
    private final ByokConnectionResolver byokResolver;
    private final ModelCapabilityProfileService profileService;

    public LanguageRouter(TaskRoutingRuleRepository routingRepository,
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
                ModelTask.LANGUAGE_AGENT);
        if (rule.isEmpty() || rule.get().connectionId() == null) {
            // 未配置规则或用户显式选择平台：平台钱包路径。
            return ModelRouteDecision.platform();
        }

        ModelRouteDecision decision = byokResolver.resolveByok(subjectId,
                rule.get().connectionId(), "语言");
        ModelConnection connection = decision.connection();
        com.li.lipicturecloud.domain.airuntime.ModelCapabilityProfile profile =
                profileService.findLatest(connection.id())
                        .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR,
                                "语言任务路由的连接尚未生成能力画像，请先测试连接"));
        if (!profile.text()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "语言任务路由的连接模型不支持文本能力，请修复或清除路由规则");
        }
        return decision;
    }
}
