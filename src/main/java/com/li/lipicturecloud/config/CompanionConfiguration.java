package com.li.lipicturecloud.config;

import com.li.lipicturecloud.application.companion.PictureNutritionAnalyzer;
import com.li.lipicturecloud.application.companion.PictureObservationProvider;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.infrastructure.companion.DemoPictureNutritionAdapter;
import com.li.lipicturecloud.infrastructure.companion.MetadataPictureNutritionAdapter;
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

    @Bean
    public PictureNutritionAnalyzer pictureNutritionAnalyzer(CompanionFeatureProperties properties,
                                                             PictureObservationProvider observations) {
        return switch (properties.getNutritionMode()) {
            case DEMO_DETERMINISTIC -> new DemoPictureNutritionAdapter();
            case METADATA_DETERMINISTIC -> new MetadataPictureNutritionAdapter(observations);
            // 视觉实现会在下一功能单元注入受控内容读取、配额和 Provider；此处绝不能静默
            // 降级，否则部署者会以为图片已经交给模型理解。
            case VISUAL_MODEL -> throw new IllegalStateException("视觉营养尚未配置");
        };
    }
}
