package com.li.lipicturecloud.manager.auth;

import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.manager.auth.model.SpaceAuthorizationResource;
import com.li.lipicturecloud.model.enums.SpaceRoleEnum;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.*;

@Component
public class SpaceAuthorizationManager implements AuthorizationManager {

    private static final Set<String> PUBLIC_OWNER_PERMISSIONS = Set.of(
            PICTURE_VIEW, PICTURE_EDIT, PICTURE_DELETE
    );

    private static final Set<String> PRIVATE_OWNER_PERMISSIONS = Set.of(
            SPACE_VIEW, SPACE_EDIT, SPACE_MANAGE,
            PICTURE_VIEW, PICTURE_UPLOAD, PICTURE_EDIT, PICTURE_DELETE
    );

    private final SpaceUserAuthManager rolePermissionManager;

    public SpaceAuthorizationManager(SpaceUserAuthManager rolePermissionManager) {
        this.rolePermissionManager = rolePermissionManager;
    }

    @Override
    public Set<String> getPermissions(
            AuthorizationSubject subject,
            SpaceAuthorizationResource resource
    ) {
        if (resource == null) {
            return Set.of();
        }
        return switch (resource.type()) {
            case PUBLIC_PICTURE -> publicPicturePermissions(subject, resource.ownerId());
            case PRIVATE_SPACE -> privateSpacePermissions(subject, resource.ownerId());
            case TEAM_SPACE -> teamSpacePermissions(subject, resource.memberRole());
        };
    }

    private Set<String> publicPicturePermissions(AuthorizationSubject subject, Long ownerId) {
        if (subject != null && (subject.platformAdmin() || subject.userId().equals(ownerId))) {
            return PUBLIC_OWNER_PERMISSIONS;
        }
        return Set.of(PICTURE_VIEW);
    }

    private Set<String> privateSpacePermissions(AuthorizationSubject subject, Long ownerId) {
        if (subject == null || (!subject.platformAdmin() && !subject.userId().equals(ownerId))) {
            return Set.of();
        }
        return PRIVATE_OWNER_PERMISSIONS;
    }

    private Set<String> teamSpacePermissions(AuthorizationSubject subject, String memberRole) {
        if (subject == null) {
            return Set.of();
        }
        String effectiveRole = subject.platformAdmin()
                ? SpaceRoleEnum.ADMIN.getValue()
                : memberRole;
        return Set.copyOf(new LinkedHashSet<>(rolePermissionManager.getPermissionsByRole(effectiveRole)));
    }
}
