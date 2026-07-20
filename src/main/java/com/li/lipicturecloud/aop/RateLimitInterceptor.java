package com.li.lipicturecloud.aop;

import com.li.lipicturecloud.annotation.RateLimit;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.model.vo.UserVO;
import com.li.lipicturecloud.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * 速率限制 AOP 切面 — 基于 Redis 滑动窗口计数器
 */
@Slf4j
@Aspect
@Component
public class RateLimitInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserService userService;

    @Around("@annotation(rateLimit)")
    public Object doIntercept(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();

        // 基于用户 ID 限流（未登录用户基于 IP）
        String userId;
        try {
            UserVO user = userService.getLoginUser(request);
            userId = String.valueOf(user.getId());
        } catch (Exception e) {
            userId = "ip:" + request.getRemoteAddr();
        }

        String key = "rate_limit:ai_chat:" + userId;
        Long current = stringRedisTemplate.opsForValue().increment(key);

        if (current == 1) {
            stringRedisTemplate.expire(key, rateLimit.windowSeconds(), TimeUnit.SECONDS);
        }

        if (current != null && current > rateLimit.maxRequests()) {
            log.warn("[速率限制] 用户 {} 超过限制 {}/{}s", userId, rateLimit.maxRequests(), rateLimit.windowSeconds());
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, rateLimit.message());
        }

        return joinPoint.proceed();
    }
}
