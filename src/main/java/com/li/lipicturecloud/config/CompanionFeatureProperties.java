package com.li.lipicturecloud.config;

import com.li.lipicturecloud.domain.companion.NutritionMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.net.URI;
import java.time.Duration;

@Getter
@Setter
/**
 * 伙伴功能的运维开关。
 * {@code enabled} 控制整个 HTTP 功能是否注册，{@code feedingEnabled} 可在保留历史可读的
 * 前提下暂停新的喂养；超时值只影响可重新开始的 PROCESSING run，不会改变已完成成长。
 */
@ConfigurationProperties(prefix = "app.companion")
public class CompanionFeatureProperties {
    private boolean enabled = true;
    private boolean feedingEnabled = true;
    private Duration processingTimeout = Duration.ofMinutes(5);
    private NutritionMode nutritionMode = NutritionMode.METADATA_DETERMINISTIC;
    /** DashScope OpenAI-compatible vision endpoint. Production config must explicitly override it. */
    private URI visionEndpoint = URI.create("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");
    private String visionProvider = "dashscope";
    private String visionModel = "qwen3.6-flash";
    private String visionApiKey = "";
    private Duration visionTimeout = Duration.ofSeconds(20);
    private DataSize visionMaxBytes = DataSize.ofMegabytes(8);
    private int visionDailyLimit = 10;
}
