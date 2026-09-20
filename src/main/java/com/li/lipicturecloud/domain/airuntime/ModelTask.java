package com.li.lipicturecloud.domain.airuntime;

/**
 * 模型网关的固定任务槽位。能力按任务匹配，不按品牌名猜测。
 * CONNECTIVITY_CHECK 是网关运维探测，不参与路由规则。
 */
public enum ModelTask {
    LANGUAGE_AGENT,
    VISION_UNDERSTANDING,
    IMAGE_CREATION,
    CONNECTIVITY_CHECK
}
