package com.li.lipicturecloud.config;

import com.li.lipicturecloud.application.airuntime.CredentialCipher;
import com.li.lipicturecloud.application.airuntime.EndpointAllowlist;
import com.li.lipicturecloud.infrastructure.airuntime.AesGcmCredentialCipher;
import com.li.lipicturecloud.infrastructure.airuntime.PropertyEndpointAllowlist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 模型网关基础设施装配：凭据加密器与端点白名单。
 */
@Configuration
@EnableConfigurationProperties(ModelCredentialProperties.class)
public class ModelGatewayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ModelGatewayConfiguration.class);

    /** 仅限本地开发的 32 字节固定密钥明文，绝不允许出现在生产配置中。 */
    private static final String DEV_ONLY_KEY_MATERIAL = "dev-only-model-credential-key-01";

    @Bean
    public CredentialCipher modelCredentialCipher(ModelCredentialProperties properties,
                                                 Environment environment) {
        String masterKey = properties.getMasterKey();
        if (masterKey == null || masterKey.isBlank()) {
            if (!isDevelopmentEnvironment(environment)) {
                throw new IllegalStateException(
                        "MODEL_CREDENTIAL_MASTER_KEY 未设置：非本地环境必须提供独立主密钥");
            }
            log.warn("MODEL_CREDENTIAL_MASTER_KEY 未设置，回退到仅限本地开发的固定密钥");
            masterKey = Base64.getEncoder().encodeToString(
                    DEV_ONLY_KEY_MATERIAL.getBytes(StandardCharsets.UTF_8));
        }
        return new AesGcmCredentialCipher(masterKey);
    }

    @Bean
    public EndpointAllowlist modelEndpointAllowlist(ModelCredentialProperties properties) {
        return new PropertyEndpointAllowlist(properties.getEndpointAllowlist());
    }

    private static boolean isDevelopmentEnvironment(Environment environment) {
        return environment.acceptsProfiles(Profiles.of("local", "test", "e2e"));
    }
}
