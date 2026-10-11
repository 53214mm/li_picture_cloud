package com.li.lipicturecloud.application.airuntime.view;

import com.li.lipicturecloud.domain.airuntime.ModelProvider;

/**
 * 当前语言模型的摘要：用户自己的模型、平台默认，或已失效的自有连接。
 */
public record QuickConnectionView(Status status, ModelProvider provider, String providerLabel,
                                  String modelCode, Long connectionId) {

    public enum Status {
        /** 未绑定自己的模型，语言任务使用平台默认。 */
        PLATFORM,
        /** 已绑定并启用自己的模型。 */
        CONNECTED,
        /** 绑定的连接已删除、停用或缺少凭据，语言任务会报错直到重新连接或改用平台。 */
        BROKEN
    }

    public static QuickConnectionView platform() {
        return new QuickConnectionView(Status.PLATFORM, null, null, null, null);
    }
}
