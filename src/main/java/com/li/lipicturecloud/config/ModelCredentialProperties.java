package com.li.lipicturecloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 模型凭据与端点白名单配置。主密钥来自环境变量 MODEL_CREDENTIAL_MASTER_KEY，
 * 生产环境缺失时启动失败；本地/测试/e2e 环境才允许回退到固定开发密钥。
 */
@Data
@ConfigurationProperties(prefix = "app.model.credential")
public class ModelCredentialProperties {

    /** 32 字节主密钥（64 位十六进制或 Base64）；生产必须由部署者提供。 */
    private String masterKey = "";

    /** 端点主机后缀白名单（仅 HTTPS）。 */
    private List<String> endpointAllowlist = List.of(
            "api.deepseek.com",
            "api.openai.com",
            "api.anthropic.com",
            "generativelanguage.googleapis.com",
            "api.moonshot.cn",
            "dashscope.aliyuncs.com",
            "api.mistral.ai",
            "open.bigmodel.cn",
            "api.siliconflow.cn");

    /** 连接探测的超时预算。 */
    private Duration connectivityTimeout = Duration.ofSeconds(5);

    /** 语言模型流式调用的超时预算。 */
    private Duration languageTimeout = Duration.ofSeconds(60);
}
