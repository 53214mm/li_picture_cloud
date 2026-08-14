package com.li.lipicturecloud.config;

import com.li.lipicturecloud.application.airuntime.CredentialCipher;
import com.li.lipicturecloud.application.airuntime.EndpointAllowlist;
import com.li.lipicturecloud.application.airuntime.ModelConnectivityTester;
import com.li.lipicturecloud.infrastructure.airuntime.AesGcmCredentialCipher;
import com.li.lipicturecloud.infrastructure.airuntime.OpenAiCompatibleConnectivityTester;
import com.li.lipicturecloud.infrastructure.airuntime.OpenAiCompatibleImageClient;
import com.li.lipicturecloud.infrastructure.airuntime.OpenAiCompatibleLanguageClient;
import com.li.lipicturecloud.infrastructure.airuntime.PropertyEndpointAllowlist;
import com.li.lipicturecloud.infrastructure.airuntime.StaticModelCapabilityRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    @Bean
    @ConditionalOnProperty(prefix = "app.model.credential", name = "connectivity-stub",
            havingValue = "false", matchIfMissing = true)
    public ModelConnectivityTester modelConnectivityTester(ModelCredentialProperties properties) {
        return OpenAiCompatibleConnectivityTester.production(properties.getConnectivityTimeout());
    }

    /** E2E 专用：连接探测不发真实外网请求，返回固定成功。 */
    @Bean
    @ConditionalOnProperty(prefix = "app.model.credential", name = "connectivity-stub",
            havingValue = "true")
    public ModelConnectivityTester stubbedModelConnectivityTester() {
        return (endpointUri, apiKey, provider) ->
                com.li.lipicturecloud.application.airuntime.ConnectivityResult.success();
    }

    @Bean
    public com.li.lipicturecloud.application.airuntime.LanguageModelInvoker modelLanguageInvoker(
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            ModelCredentialProperties properties) {
        return OpenAiCompatibleLanguageClient.production(objectMapper, properties.getLanguageTimeout());
    }

    @Bean
    public com.li.lipicturecloud.application.airuntime.ModelCapabilityRegistry modelCapabilityRegistry() {
        return new StaticModelCapabilityRegistry();
    }

    @Bean
    public com.li.lipicturecloud.application.airuntime.ImageModelInvoker modelImageInvoker(
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            ModelCredentialProperties properties) {
        return OpenAiCompatibleImageClient.production(objectMapper, properties.getImageTimeout());
    }

    private static boolean isDevelopmentEnvironment(Environment environment) {
        return environment.acceptsProfiles(Profiles.of("local", "test", "e2e"));
    }
}
