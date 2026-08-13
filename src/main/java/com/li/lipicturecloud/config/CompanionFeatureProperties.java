package com.li.lipicturecloud.config;

import com.li.lipicturecloud.domain.companion.NutritionPolicy;
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
    /**
     * 请求策略描述这次喂养允许走的路径；真正采用的来源由成长记录中的 provenance 保存。
     *
     * <p>不要把它命名为 mode：视觉策略可能安全降级为元数据，而 {@code mode} 容易让调用方
     * 把“允许调用视觉模型”误读成“这次已经理解了图片内容”。</p>
     */
    private NutritionPolicy nutritionPolicy = NutritionPolicy.METADATA_ONLY;
    /** DashScope OpenAI-compatible vision endpoint. Production config must explicitly override it. */
    private URI visionEndpoint = URI.create("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");
    private String visionProvider = "dashscope";
    private String visionModel = "qwen3.6-flash";
    private String visionApiKey = "";
    private Duration visionTimeout = Duration.ofSeconds(20);
    private DataSize visionMaxBytes = DataSize.ofMegabytes(8);
    private int visionDailyLimit = 10;
}
