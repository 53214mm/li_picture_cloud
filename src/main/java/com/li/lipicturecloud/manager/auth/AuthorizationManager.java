package com.li.lipicturecloud.manager.auth;

import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.manager.auth.model.SpaceAuthorizationResource;

import java.util.Set;

public interface AuthorizationManager {

    Set<String> getPermissions(
            AuthorizationSubject subject,
            SpaceAuthorizationResource resource
    );

    default boolean hasPermission(
            AuthorizationSubject subject,
            SpaceAuthorizationResource resource,
            String permission
    ) {
        return getPermissions(subject, resource).contains(permission);
    }
}
