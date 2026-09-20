package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.ModelCapabilities;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;

/**
 * 模型能力认知端口：返回平台维护的 (provider, modelCode) 能力快照。
 * 未知组合一律返回 {@link ModelCapabilities#unknown()}（全部不支持）。
 */
public interface ModelCapabilityRegistry {

    ModelCapabilities capabilitiesFor(ModelProvider provider, String modelCode);
}
