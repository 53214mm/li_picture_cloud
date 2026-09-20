package com.li.lipicturecloud.application.airuntime;

import java.util.Objects;

/**
 * 一次对话轮次：只含角色与正文，不含任何凭据信息。
 */
public record ChatTurn(String role, String content) {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public ChatTurn {
        if (!ROLE_SYSTEM.equals(role) && !ROLE_USER.equals(role) && !ROLE_ASSISTANT.equals(role)) {
            throw new IllegalArgumentException("unsupported chat role: " + role);
        }
        Objects.requireNonNull(content, "content");
    }

    public static ChatTurn system(String content) {
        return new ChatTurn(ROLE_SYSTEM, content);
    }

    public static ChatTurn user(String content) {
        return new ChatTurn(ROLE_USER, content);
    }

    public static ChatTurn assistant(String content) {
        return new ChatTurn(ROLE_ASSISTANT, content);
    }
}
