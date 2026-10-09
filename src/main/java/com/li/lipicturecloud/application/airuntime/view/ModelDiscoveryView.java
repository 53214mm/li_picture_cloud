package com.li.lipicturecloud.application.airuntime.view;

import com.li.lipicturecloud.domain.airuntime.ModelProvider;

import java.util.List;

/**
 * 快速连接第一步的结果：凭据可用后可供选择的模型与默认选中项。
 *
 * @param listed 为 false 时列表来自平台推荐（供应商未开放模型目录）
 */
public record ModelDiscoveryView(ModelProvider provider, String providerLabel,
                                 List<String> models, String recommendedModel, boolean listed) {

    public ModelDiscoveryView {
        models = List.copyOf(models);
    }
}
