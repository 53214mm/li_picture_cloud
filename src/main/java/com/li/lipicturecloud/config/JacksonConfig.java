package com.li.lipicturecloud.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson JSON 序列化配置
 * <p>
 * 解决雪花算法生成的 Long 型 ID 在前端 JavaScript 中精度丢失的问题：
 * JS 的 Number 类型安全整数范围为 ±2⁵³−1，
 * 而雪花 ID 通常为 18-19 位十进制数，超出该范围。
 * 全局配置 Long → String 序列化，前端收到的是字符串，不会再丢失尾数。
 */
@Configuration
public class JacksonConfig {

    /**
     * 将全局 Long 类型序列化为 String
     * <p>
     * 同时处理包装类型 {@link Long} 和基本类型 {@code long}，
     * 避免 ID 字段在前端出现 {@code 1823456789012345678 → 1823456789012345700} 的精度丢失。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            // Long 包装类型 → String
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            // long 基本类型 → String
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}
