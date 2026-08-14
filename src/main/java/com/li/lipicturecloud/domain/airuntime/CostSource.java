package com.li.lipicturecloud.domain.airuntime;

/**
 * 一次模型调用的费用来源。BYOK 失败不得静默切换平台钱包。
 */
public enum CostSource {
    BYOK,
    PLATFORM
}
