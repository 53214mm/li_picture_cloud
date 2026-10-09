package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.ModelProvider;

import java.net.URI;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 快速连接用的供应商预设：平台维护的 OpenAI 兼容端点与推荐模型。
 *
 * <p>用户只选供应商，端点由这里决定，因此端点仍须命中白名单；推荐模型只是默认选中项与
 * 目录不可读时的备选，是否真能对话由最小对话验证决定，不按名称猜测能力。</p>
 */
public final class ModelProviderPresets {

    /** 一个供应商的预设。{@code suggestedModels} 第一项为推荐模型。 */
    public record Preset(ModelProvider provider, String label, URI endpoint,
                         List<String> suggestedModels) {

        public Preset {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(endpoint, "endpoint");
            suggestedModels = List.copyOf(suggestedModels);
            if (label == null || label.isBlank() || suggestedModels.isEmpty()) {
                throw new IllegalArgumentException("preset requires a label and a suggested model");
            }
        }

        public String recommendedModel() {
            return suggestedModels.get(0);
        }
    }

    private static final Map<ModelProvider, Preset> PRESETS = new EnumMap<>(ModelProvider.class);

    static {
        register(ModelProvider.DEEPSEEK, "DeepSeek", "https://api.deepseek.com/v1",
                "deepseek-chat", "deepseek-reasoner");
        register(ModelProvider.OPENAI, "OpenAI", "https://api.openai.com/v1",
                "gpt-4o-mini", "gpt-4o", "gpt-4.1-mini");
        register(ModelProvider.ANTHROPIC, "Anthropic", "https://api.anthropic.com/v1",
                "claude-sonnet-5-5", "claude-haiku-4-5-20251001");
        register(ModelProvider.GOOGLE, "Google Gemini",
                "https://generativelanguage.googleapis.com/v1beta/openai",
                "gemini-2.5-flash", "gemini-2.5-pro");
        register(ModelProvider.MOONSHOT, "Kimi", "https://api.moonshot.cn/v1",
                "moonshot-v1-8k", "moonshot-v1-32k");
        register(ModelProvider.DASHSCOPE, "通义千问",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "qwen-plus", "qwen-max", "qwen-turbo");
        register(ModelProvider.SILICONFLOW, "硅基流动", "https://api.siliconflow.cn/v1",
                "deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-7B-Instruct");
        register(ModelProvider.ZHIPU, "智谱", "https://open.bigmodel.cn/api/paas/v4",
                "glm-4-flash", "glm-4-plus");
    }

    private ModelProviderPresets() {
    }

    private static void register(ModelProvider provider, String label, String endpoint,
                                 String... suggestedModels) {
        PRESETS.put(provider, new Preset(provider, label, URI.create(endpoint),
                List.of(suggestedModels)));
    }

    public static Preset of(ModelProvider provider) {
        Preset preset = PRESETS.get(Objects.requireNonNull(provider, "provider"));
        if (preset == null) {
            throw new IllegalStateException("no preset for provider " + provider);
        }
        return preset;
    }
}
