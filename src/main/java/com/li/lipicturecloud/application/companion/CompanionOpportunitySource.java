package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;

import java.time.Instant;
import java.util.Optional;

/**
 * 主动提案的机会源：从伙伴域已有数据感知一个候选机会。
 * 文案必须确定性生成；无候选时返回空，不产生外部调用。
 */
public interface CompanionOpportunitySource {

    /** 本机会源的机会类型：用于把"三类机会的生成与拦截"写入可观测日志。 */
    ProposalOpportunityType type();

    Optional<ProposalOpportunity> findOpportunity(long companionId, long subjectId, Instant now);
}
