package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;

import java.util.Objects;

/**
 * 一次模型任务的路由决定（语言/视觉/图像创作共用）。
 *
 * <p>BYOK 路由携带连接与已解密凭据（仅存在于本次调用内存中）；平台路由两者皆空。
 * BYOK 调用失败不得静默切换平台钱包，失败只能以安全错误码向用户暴露。</p>
 */
public record ModelRouteDecision(
        CostSource costSource,
        ModelConnection connection,
        String apiKey) {

    public ModelRouteDecision {
        Objects.requireNonNull(costSource, "costSource");
        if (costSource == CostSource.PLATFORM) {
            if (connection != null || apiKey != null) {
                throw new IllegalArgumentException("platform route must not carry connection or credential");
            }
        } else {
            Objects.requireNonNull(connection, "connection");
            if (connection.credentialId() == null) {
                throw new IllegalArgumentException("byok route requires a bound credential");
            }
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException("byok route requires a decrypted api key");
            }
        }
    }

    public static ModelRouteDecision platform() {
        return new ModelRouteDecision(CostSource.PLATFORM, null, null);
    }

    public static ModelRouteDecision byok(ModelConnection connection, String apiKey) {
        return new ModelRouteDecision(CostSource.BYOK, connection, apiKey);
    }

    public boolean isByok() {
        return costSource == CostSource.BYOK;
    }

    /** 记录的隐式 toString 会打印 apiKey 组件，这里显式遮蔽，杜绝未来日志/序列化误伤。 */
    @Override
    public String toString() {
        return "ModelRouteDecision[costSource=" + costSource
                + ", connection=" + (connection == null ? null
                : "ModelConnection[id=" + connection.id() + ", provider=" + connection.provider()
                + ", endpointUri=" + connection.endpointUri() + ", modelCode="
                + connection.modelCode() + ", credentialId=" + connection.credentialId() + "]")
                + ", apiKey=***]";
    }
}
