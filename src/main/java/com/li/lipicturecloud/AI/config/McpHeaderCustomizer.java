package com.li.lipicturecloud.AI.config;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpRequest;

/**
 * MCP HTTP 鉴权定制器
 * <p>
 * 实现 McpSyncHttpClientRequestCustomizer，在每次 MCP HTTP 请求前注入 Authorization 头。
 * 参考 li-ai-agent 项目。
 */
@Configuration
public class McpHeaderCustomizer {

    private static final Logger log = LoggerFactory.getLogger(McpHeaderCustomizer.class);

    @Value("${mxai.api-key}")
    private String apiKey;

    @Bean
    public McpSyncHttpClientRequestCustomizer mcpAuthCustomizer() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("MCP API Key 未配置 (mxai.api-key)，MCP 工具将不可用");
        } else {
            log.info("MCP 鉴权定制器已注册，API Key 长度: {}", apiKey.length());
        }
        String key = apiKey != null ? apiKey : "";
        return (HttpRequest.Builder requestBuilder,
                String serverName,
                URI uri,
                String method,
                McpTransportContext context) -> {
            if (key.isBlank()) {
                throw new IllegalStateException("MCP API Key 未配置");
            }
            requestBuilder.header("Authorization", key);
        };
    }
}
