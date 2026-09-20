package com.li.lipicturecloud.domain.companion;

import java.util.List;
import java.util.Optional;

/**
 * 主动提案的持久化端口。
 */
public interface CompanionProposalRepository {

    CompanionProposal append(CompanionProposal proposal);

    Optional<CompanionProposal> findById(long id);

    /** 仍处于 PENDING 的提案（同一伙伴最多应有一条）。 */
    List<CompanionProposal> findActive(long companionId, int limit);

    /** 最近提案（全部状态），用于频率守门。 */
    List<CompanionProposal> findRecent(long companionId, int limit);

    boolean save(CompanionProposal after, long expectedRevision);
}
