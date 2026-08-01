package com.li.lipicturecloud.domain.space;

import java.util.Arrays;

public enum SpaceMemberRole {
    VIEWER("viewer"),
    EDITOR("editor"),
    ADMIN("admin");

    private final String value;

    SpaceMemberRole(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static SpaceMemberRole from(String value) {
        return Arrays.stream(values())
                .filter(role -> role.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知空间成员角色: " + value));
    }
}
