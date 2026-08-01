package com.li.lipicturecloud.manager.auth.model;

import java.util.Objects;

public record AuthorizationSubject(Long userId, boolean platformAdmin) {

    public AuthorizationSubject {
        Objects.requireNonNull(userId, "userId must not be null");
    }

    public static AuthorizationSubject user(Long userId) {
        return new AuthorizationSubject(userId, false);
    }

    public static AuthorizationSubject platformAdmin(Long userId) {
        return new AuthorizationSubject(userId, true);
    }
}
