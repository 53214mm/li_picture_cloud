package com.li.lipicturecloud.config;

import com.li.lipicturecloud.application.airuntime.FusionArtworkSaver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 图片炼金基础设施装配。E2E 下融合作品回库不经过真实对象存储管线，
 * 返回固定作品 ID（保存状态机与血缘链路照常执行）。
 */
@Configuration
public class CreationConfiguration {

    /** E2E 专用：回库保存 stub，不再走 COS 上传。 */
    @Bean
    @ConditionalOnProperty(prefix = "app.creation", name = "artwork-stub", havingValue = "true")
    public FusionArtworkSaver stubbedFusionArtworkSaver() {
        return request -> 99001L;
    }
}
