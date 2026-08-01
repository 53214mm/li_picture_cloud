package com.li.lipicturecloud.domain.space;

public record SpaceMembership(Long id, Long spaceId, Long userId, SpaceMemberRole role) {

    public SpaceMembership {
        if (spaceId == null || userId == null || role == null) {
            throw new IllegalArgumentException("空间 ID、用户 ID 和成员角色不能为空");
        }
    }

    public static SpaceMembership restore(Long id, Long spaceId, Long userId, String role) {
        return new SpaceMembership(id, spaceId, userId, SpaceMemberRole.from(role));
    }
}
