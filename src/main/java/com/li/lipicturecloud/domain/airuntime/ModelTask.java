package com.li.lipicturecloud.domain.airuntime;

/**
 * 模型网关的固定任务路由槽位。能力按任务匹配，不按品牌名猜测。
 */
public enum ModelTask {
    LANGUAGE_AGENT,
    VISION_UNDERSTANDING,
    IMAGE_CREATION
}
