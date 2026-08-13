package com.li.lipicturecloud.domain.companion;

import java.util.Optional;

public interface CompanionRepository {
    Optional<Companion> findByOwnerId(long ownerId);
    Optional<Companion> findByOwnerIdForUpdate(long ownerId);
    Companion createIfAbsent(long ownerId, CompanionBalance balance);
    boolean save(Companion companionAfter, long expectedRevision);
}
