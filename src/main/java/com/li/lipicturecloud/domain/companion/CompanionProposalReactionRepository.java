package com.li.lipicturecloud.domain.companion;

import java.time.Instant;

/**
 * 提案反馈的持久化端口。
 */
public interface CompanionProposalReactionRepository {

    CompanionProposalReaction append(CompanionProposalReaction reaction);

    /** 某主体在 since 之后敲打提案的次数（用于"重复负反馈才缓慢改变性格"）。 */
    long countScoldsSince(long subjectId, Instant since);
}
