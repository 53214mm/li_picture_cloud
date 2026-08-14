package com.li.lipicturecloud.application.airuntime.view;

import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRule;

/**
 * 任务路由规则的安全展示视图。
 */
public record ModelRoutingRuleView(
        long id,
        ModelTask task,
        Long connectionId,
        long revision) {

    public static ModelRoutingRuleView of(TaskRoutingRule rule) {
        return new ModelRoutingRuleView(rule.id(), rule.task(), rule.connectionId(),
                rule.revision());
    }
}
