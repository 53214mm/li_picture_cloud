package com.li.lipicturecloud.application.companion;

import java.time.Instant;
import java.util.Optional;

/**
 * 主动提案的机会源：从伙伴域已有数据感知一个候选机会。
 * 文案必须确定性生成；无候选时返回空，不产生外部调用。
 */
public interface CompanionOpportunitySource {

    Optional<ProposalOpportunity> findOpportunity(long companionId, long subjectId, Instant now);
}
