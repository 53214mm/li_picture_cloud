package com.li.lipicturecloud.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
}
