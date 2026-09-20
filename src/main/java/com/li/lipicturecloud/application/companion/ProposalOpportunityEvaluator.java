package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.CompanionMood;
import com.li.lipicturecloud.domain.companion.CompanionMoodRepository;
import com.li.lipicturecloud.domain.companion.CompanionMoodRules;
import com.li.lipicturecloud.domain.companion.CompanionRelationship;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 主动提案的冲动评估：情绪/关系 → 0-100 冲动得分，以及"是否值得生成候选"的阈值决策。
 *
 * <p>第一版口径（Q2 验收清单）：冲动得分由"当前情绪（读取前按完整小时惰性衰减，
 * 与主页/聊天同口径）+ 与当前主体的关系"按场景权重计算，只影响"是否生成候选"；
 * 没有任何正向情绪/关系积累（得分为 0）时伙伴不主动。好奇性格参与、短期好奇冲动
 * 与独立衰减属于正式第二季度冲动机制的后续内容，不在本组件内模拟。</p>
 */
@Component
public class ProposalOpportunityEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ProposalOpportunityEvaluator.class);
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private final CompanionMoodRepository moodRepository;
    private final CompanionRelationshipRepository relationshipRepository;
    private final CompanionMoodRules moodRules;
    private final Clock clock;

    public ProposalOpportunityEvaluator(CompanionMoodRepository moodRepository,
                                        CompanionRelationshipRepository relationshipRepository,
                                        CompanionMoodRules moodRules,
                                        Clock clock) {
        this.moodRepository = moodRepository;
        this.relationshipRepository = relationshipRepository;
        this.moodRules = moodRules;
        this.clock = clock;
    }

    /**
     * 场景化冲动得分：衰减后愉悦 × joyWeight + 熟悉度 × familiarityWeight，钳制在 [0,100]。
     */
    public BigDecimal score(long companionId, long subjectId,
                            BigDecimal joyWeight, BigDecimal familiarityWeight) {
        Objects.requireNonNull(joyWeight, "joyWeight");
        Objects.requireNonNull(familiarityWeight, "familiarityWeight");
        BigDecimal joy = currentJoy(companionId);
        BigDecimal familiarity = relationshipRepository
                .findByCompanionAndSubject(companionId, subjectId)
                .map(CompanionRelationship::familiarity)
                .orElse(BigDecimal.ZERO);
        return joy.multiply(joyWeight)
                .add(familiarity.multiply(familiarityWeight))
                .setScale(2, RoundingMode.HALF_UP)
                .max(ZERO).min(HUNDRED);
    }

    /** 得分是否达到生成候选的最低冲动（第一版：存在正向积累才主动，零积累不打扰）。 */
    public boolean reachesProposalThreshold(BigDecimal score) {
        return Objects.requireNonNull(score, "score").signum() > 0;
    }

    /**
     * 读取"当前愉悦"：先按完整小时惰性衰减，再以 revision CAS 条件写回；
     * 与喂养/主页写竞争失败时丢弃本次衰减写，下次读取重新计算。没有情绪行视为 0。
     */
    private BigDecimal currentJoy(long companionId) {
        Instant now = clock.instant();
        return moodRepository.findByCompanionId(companionId)
                .map(existing -> {
                    CompanionMood decayed = existing.decayed(now, moodRules);
                    if (decayed != existing && !moodRepository.save(decayed,
                            Math.subtractExact(decayed.revision(), 1L))) {
                        log.warn("companion_proposal_mood_decay_conflict companionId={}", companionId);
                    }
                    return decayed.joy();
                })
                .orElse(BigDecimal.ZERO);
    }
}
