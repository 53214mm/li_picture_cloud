package com.li.lipicturecloud.domain.space;

import java.util.Optional;

public interface SpaceMembershipRepository {
    Optional<SpaceMembership> findById(Long membershipId);

    Optional<SpaceMembership> findBySpaceAndUser(Long spaceId, Long userId);
}
