package com.li.lipicturecloud.manager.auth.model;

import java.util.Objects;

public record SpaceAuthorizationResource(
        SpaceAuthorizationResourceType type,
        Long ownerId,
        String memberRole
) {

    public SpaceAuthorizationResource {
        Objects.requireNonNull(type, "type must not be null");
    }

    public static SpaceAuthorizationResource publicPicture(Long ownerId) {
        return new SpaceAuthorizationResource(
                SpaceAuthorizationResourceType.PUBLIC_PICTURE,
                Objects.requireNonNull(ownerId, "ownerId must not be null"),
                null
        );
    }

    public static SpaceAuthorizationResource privateSpace(Long ownerId) {
        return new SpaceAuthorizationResource(
                SpaceAuthorizationResourceType.PRIVATE_SPACE,
                Objects.requireNonNull(ownerId, "ownerId must not be null"),
                null
        );
    }

    public static SpaceAuthorizationResource teamSpace(String memberRole) {
        return new SpaceAuthorizationResource(SpaceAuthorizationResourceType.TEAM_SPACE, null, memberRole);
    }
}
