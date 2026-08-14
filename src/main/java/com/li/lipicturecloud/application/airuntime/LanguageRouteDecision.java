package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;

import java.util.Objects;

/**
 * 语言任务的一次路由决定。
 *
 * <p>BYOK 路由携带连接与已解密凭据（仅存在于本次调用内存中）；平台路由两者皆空。
 * BYOK 调用失败不得静默切换平台钱包，失败只能以安全错误码向用户暴露。</p>
 */
public record LanguageRouteDecision(
        CostSource costSource,
        ModelConnection connection,
        String apiKey) {

    public LanguageRouteDecision {
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

    public static LanguageRouteDecision platform() {
        return new LanguageRouteDecision(CostSource.PLATFORM, null, null);
    }

    public static LanguageRouteDecision byok(ModelConnection connection, String apiKey) {
        return new LanguageRouteDecision(CostSource.BYOK, connection, apiKey);
    }

    public boolean isByok() {
        return costSource == CostSource.BYOK;
    }
}
