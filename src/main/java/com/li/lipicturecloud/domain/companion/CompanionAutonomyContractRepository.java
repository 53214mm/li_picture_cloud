package com.li.lipicturecloud.domain.companion;

import java.util.Optional;

/**
 * 自主契约的持久化端口。
 */
public interface CompanionAutonomyContractRepository {

    Optional<CompanionAutonomyContract> findByCompanionAndSubject(long companionId, long subjectId);

    CompanionAutonomyContract createIfAbsent(long companionId, long subjectId);

    boolean save(CompanionAutonomyContract after, long expectedRevision);
}
