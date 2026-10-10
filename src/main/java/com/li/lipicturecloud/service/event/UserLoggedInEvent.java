package com.li.lipicturecloud.service.event;

import java.time.Instant;
import java.util.Objects;

/**
 * 用户登录成功。只带用户 id 和时间，不带账号、密码或会话信息。
 */
public record UserLoggedInEvent(long userId, Instant loginTime) {

    public UserLoggedInEvent {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Objects.requireNonNull(loginTime, "loginTime");
    }
}
