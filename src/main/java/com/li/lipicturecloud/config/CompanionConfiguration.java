package com.li.lipicturecloud.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.companion.AuthorizedPictureContentProvider;
import com.li.lipicturecloud.application.companion.PictureNutritionAnalyzer;
import com.li.lipicturecloud.application.companion.PictureObservationProvider;
import com.li.lipicturecloud.application.companion.VisualObservationProvider;
import com.li.lipicturecloud.application.companion.VisionQuotaGuard;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.infrastructure.companion.DashScopeVisionClient;
import com.li.lipicturecloud.infrastructure.companion.DemoPictureNutritionAdapter;
import com.li.lipicturecloud.infrastructure.companion.MetadataPictureNutritionAdapter;
import com.li.lipicturecloud.infrastructure.companion.VisualPictureNutritionAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(CompanionFeatureProperties.class)
public class CompanionConfiguration {

    @Bean
    public CompanionBalance companionBalance() {
        // 通过 Bean 注入而非在服务里 new，测试能替换 Clock，未来也能显式选择平衡版本。
        return CompanionBalance.v1();
    }

    @Bean
    public Clock companionClock() {
        // 所有时间规则从这里获得时间，避免业务代码直接调用 Instant.now() 而难以复现边界测试。
        return Clock.systemUTC();
    }

    /**
     * Demo 与视觉/元数据实现分成互斥 Bean，测试和 E2E 无论是否设置真实 API key 都不会创建网络客户端。
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.companion", name = "nutrition-policy", havingValue = "DEMO_ONLY")
    public PictureNutritionAnalyzer demoPictureNutritionAnalyzer() {
        return new DemoPictureNutritionAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.companion", name = "nutrition-policy",
            havingValue = "METADATA_ONLY", matchIfMissing = true)
    public PictureNutritionAnalyzer metadataPictureNutritionAnalyzer(PictureObservationProvider observations) {
        return new MetadataPictureNutritionAdapter(observations);
    }

    /**
     * 只在显式视觉模式创建客户端；空 API key 会在这里快速失败，绝不静默降级成元数据模式。
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.companion", name = "nutrition-policy",
            havingValue = "VISUAL_WITH_METADATA_FALLBACK")
    public VisualObservationProvider dashScopeVisualObservationProvider(ObjectMapper objectMapper,
                                                                          CompanionFeatureProperties properties) {
        return DashScopeVisionClient.fromProperties(objectMapper, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.companion", name = "nutrition-policy",
            havingValue = "VISUAL_WITH_METADATA_FALLBACK")
    public PictureNutritionAnalyzer visualPictureNutritionAnalyzer(CompanionFeatureProperties properties,
                                                                    PictureObservationProvider observations,
                                                                    VisionQuotaGuard quota,
                                                                    AuthorizedPictureContentProvider contents,
                                                                    VisualObservationProvider visual,
                                                                    Clock clock) {
        return new VisualPictureNutritionAdapter(quota, contents, visual,
                new MetadataPictureNutritionAdapter(observations), clock,
                properties.getVisionMaxBytes().toBytes(), properties.getVisionDailyLimit());
    }
}
