package com.li.lipicturecloud.manager.auth;

import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.manager.auth.model.SpaceAuthorizationResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaceAuthorizationManagerTest {

    private AuthorizationManager authorizationManager;

    @BeforeEach
    void setUp() {
        authorizationManager = new SpaceAuthorizationManager(new SpaceUserAuthManager());
    }

    @Test
    void publicPictureIsVisibleToAnonymousUsers() {
        Set<String> permissions = authorizationManager.getPermissions(
                null,
                SpaceAuthorizationResource.publicPicture(10L)
        );

        assertThat(permissions).containsExactly("picture:view");
    }

    @Test
    void publicPictureOwnerCanEditButCannotCollaborate() {
        Set<String> permissions = authorizationManager.getPermissions(
                AuthorizationSubject.user(10L),
                SpaceAuthorizationResource.publicPicture(10L)
        );

        assertThat(permissions)
                .contains("picture:view", "picture:edit", "picture:delete")
                .doesNotContain("collaboration:join", "collaboration:edit", "spaceUser:manage");
    }

    @Test
    void privateSpaceOnlyAllowsOwnerOrPlatformAdmin() {
        SpaceAuthorizationResource resource = SpaceAuthorizationResource.privateSpace(10L);

        assertThat(authorizationManager.getPermissions(AuthorizationSubject.user(20L), resource)).isEmpty();
        assertThat(authorizationManager.getPermissions(AuthorizationSubject.user(10L), resource))
                .contains("space:view", "space:edit", "space:manage", "picture:upload")
                .doesNotContain("collaboration:join", "spaceUser:manage");
        assertThat(authorizationManager.getPermissions(AuthorizationSubject.platformAdmin(99L), resource))
                .contains("space:view", "space:manage", "picture:delete")
                .doesNotContain("collaboration:edit");
    }

    @ParameterizedTest
    @MethodSource("teamRolePermissions")
    void teamRolesMapToExpectedPermissions(String role, Set<String> expected, Set<String> forbidden) {
        Set<String> permissions = authorizationManager.getPermissions(
                AuthorizationSubject.user(10L),
                SpaceAuthorizationResource.teamSpace(role)
        );

        assertThat(permissions).containsAll(expected);
        if (!forbidden.isEmpty()) {
            assertThat(permissions).doesNotContainAnyElementsOf(forbidden);
        }
    }

    static Stream<Arguments> teamRolePermissions() {
        return Stream.of(
                Arguments.of("viewer", Set.of("space:view", "picture:view"),
                        Set.of("picture:edit", "collaboration:join", "spaceUser:manage")),
                Arguments.of("editor", Set.of("space:view", "picture:edit", "collaboration:join", "collaboration:edit"),
                        Set.of("spaceUser:manage", "space:manage")),
                Arguments.of("admin", Set.of("space:manage", "spaceUser:manage", "collaboration:edit"), Set.of())
        );
    }

    @Test
    void unknownTeamRoleFailsClosed() {
        Set<String> permissions = authorizationManager.getPermissions(
                AuthorizationSubject.user(10L),
                SpaceAuthorizationResource.teamSpace("unknown")
        );

        assertThat(permissions).isEmpty();
    }

    @Test
    void returnedPermissionsAreImmutable() {
        Set<String> permissions = authorizationManager.getPermissions(
                AuthorizationSubject.user(10L),
                SpaceAuthorizationResource.teamSpace("viewer")
        );

        assertThatThrownBy(() -> permissions.add("picture:delete"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
