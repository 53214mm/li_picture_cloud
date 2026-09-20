package com.li.lipicturecloud.infrastructure.airuntime;

import com.li.lipicturecloud.application.airuntime.ModelCapabilityRegistry;
import com.li.lipicturecloud.domain.airuntime.ModelCapabilities;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;

import java.util.Map;
import java.util.Objects;

/**
 * 平台维护的静态能力表（设计默认值，随供应商变更评审更新）。
 * 不在此表的组合一律返回 {@link ModelCapabilities#unknown()}，绝不猜测能力。
 */
public class StaticModelCapabilityRegistry implements ModelCapabilityRegistry {

    private final Map<String, ModelCapabilities> known = Map.of(
            "DEEPSEEK/deepseek-chat", ModelCapabilities.of(true, false, true, true, false,
                    false, false, 64_000, ModelCapabilities.SYNC, ModelCapabilities.COST_CHEAP),
            "DEEPSEEK/deepseek-reasoner", ModelCapabilities.of(true, false, true, true, true,
                    false, false, 128_000, ModelCapabilities.SYNC, ModelCapabilities.COST_CHEAP),
            "DASHSCOPE/qwen3.6-flash", ModelCapabilities.of(true, true, false, true, false,
                    false, false, 32_000, ModelCapabilities.SYNC, ModelCapabilities.COST_CHEAP),
            "OPENAI/gpt-image-2", ModelCapabilities.of(false, false, false, false, false,
                    false, true, null, ModelCapabilities.UNKNOWN, ModelCapabilities.COST_EXPENSIVE));

    @Override
    public ModelCapabilities capabilitiesFor(ModelProvider provider, String modelCode) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(modelCode, "modelCode");
        return known.getOrDefault(provider.name() + "/" + modelCode, ModelCapabilities.unknown());
    }
}
