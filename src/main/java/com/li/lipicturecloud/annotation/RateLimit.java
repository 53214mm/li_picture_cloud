package com.li.lipicturecloud.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 速率限制注解 — 基于 Redis 的请求频率控制
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /** 时间窗口内的最大请求次数 */
    int maxRequests() default 10;
    /** 时间窗口（秒） */
    int windowSeconds() default 60;
    /** 超出限制时的提示信息 */
    String message() default "请求过于频繁，请稍后重试";
}
