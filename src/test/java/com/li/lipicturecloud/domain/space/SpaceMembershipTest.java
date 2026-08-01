package com.li.lipicturecloud.domain.space;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaceMembershipTest {

    @Test
    void createsMembershipFromPersistentValues() {
        SpaceMembership membership = SpaceMembership.restore(9L, 7L, 8L, "editor");

        assertThat(membership.id()).isEqualTo(9L);
        assertThat(membership.spaceId()).isEqualTo(7L);
        assertThat(membership.userId()).isEqualTo(8L);
        assertThat(membership.role()).isEqualTo(SpaceMemberRole.EDITOR);
    }

    @Test
    void rejectsUnknownRoleInsteadOfGrantingAccidentalPermissions() {
        assertThatThrownBy(() -> SpaceMembership.restore(9L, 7L, 8L, "owner"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void rejectsMissingIdentity() {
        assertThatThrownBy(() -> SpaceMembership.restore(9L, null, 8L, "viewer"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
