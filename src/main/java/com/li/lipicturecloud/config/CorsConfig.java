package com.li.lipicturecloud.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig implements WebMvcConfigurer {

    private static final Profiles DEVELOPMENT_PROFILES = Profiles.of("local", "test", "e2e");

    private final CorsProperties properties;

    public CorsConfig(CorsProperties properties, Environment environment) {
        this.properties = properties;
        // 双保险：即使生产 profile 因部署失误未被激活，只要不在开发 profile 内，
        // 全放行白名单也会在启动时失败，而不是带着 allowCredentials 的 * 上线。
        if (!environment.acceptsProfiles(DEVELOPMENT_PROFILES)
                && properties.getAllowedOriginPatterns().contains("*")) {
            throw new IllegalStateException(
                    "非开发环境必须通过 CORS_ALLOWED_ORIGINS 显式配置跨域白名单，不允许使用 *");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 覆盖所有请求
        registry.addMapping("/**")
                // 允许发送 Cookie
                .allowCredentials(true)
                // 放行白名单域名（必须用 patterns，否则 * 会和 allowCredentials 冲突）；
                // 本地默认 *，生产由 CORS_ALLOWED_ORIGINS 显式提供。
                .allowedOriginPatterns(properties.getAllowedOriginPatterns().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .maxAge(3600);
    }
}
