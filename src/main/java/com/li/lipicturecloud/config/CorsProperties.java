package com.li.lipicturecloud.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
/**
 * 跨域白名单。
 *
 * <p>本地开发默认放行所有来源；生产 profile 必须通过 {@code CORS_ALLOWED_ORIGINS}
 * 显式提供域名白名单（无默认值，缺失时启动失败），避免跨域配置被悄悄带回全放行。</p>
 */
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of("*"));
}
