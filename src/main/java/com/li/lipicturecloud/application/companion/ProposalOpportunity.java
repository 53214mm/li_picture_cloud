package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;

import java.math.BigDecimal;

/**
 * 机会源产出的候选机会：类型、冲动得分与确定性文案。
 */
public record ProposalOpportunity(
        ProposalOpportunityType type,
        BigDecimal impulseScore,
        String content) {
}
