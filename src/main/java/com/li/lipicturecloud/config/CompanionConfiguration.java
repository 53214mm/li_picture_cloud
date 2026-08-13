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
        };
    }
}
