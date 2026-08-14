package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.CompanionMood;
import com.li.lipicturecloud.domain.companion.CompanionMoodRepository;
import com.li.lipicturecloud.domain.companion.CompanionRelationship;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRepository;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 纪念日机会：往年同月同日（上海日历）至少完整喂养过一次时产生。
 *
 * <p>只依赖成长记录（不查询图片表），文案确定性生成；冲动得分同样来自情绪与关系。
 * 机会源优先级第 2（次于每周回顾，先于相似图片）。</p>
 */
@Component
@org.springframework.core.annotation.Order(2)
public class AnniversaryOpportunitySource implements CompanionOpportunitySource {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private final GrowthRecordRepository growthRepository;
    private final CompanionMoodRepository moodRepository;
    private final CompanionRelationshipRepository relationshipRepository;

    public AnniversaryOpportunitySource(GrowthRecordRepository growthRepository,
                                        CompanionMoodRepository moodRepository,
                                        CompanionRelationshipRepository relationshipRepository) {
        this.growthRepository = growthRepository;
        this.moodRepository = moodRepository;
        this.relationshipRepository = relationshipRepository;
    }

    @Override
    public Optional<ProposalOpportunity> findOpportunity(long companionId, long subjectId, Instant now) {
        LocalDate today = now.atZone(SHANGHAI).toLocalDate();
        long feeds = growthRepository.countAnniversaryFeeds(companionId,
                today.getMonthValue(), today.getDayOfMonth());
        if (feeds < 1) {
            return Optional.empty();
        }
        String content = String.format("往年的今天（%d 月 %d 日）我们相遇过。想和我一起看看那时的回忆吗？",
                today.getMonthValue(), today.getDayOfMonth());
        return Optional.of(new ProposalOpportunity(ProposalOpportunityType.ANNIVERSARY,
                impulseScore(companionId, subjectId), content));
    }

    private BigDecimal impulseScore(long companionId, long subjectId) {
        BigDecimal joy = moodRepository.findByCompanionId(companionId)
                .map(CompanionMood::joy).orElse(BigDecimal.ZERO);
        BigDecimal familiarity = relationshipRepository
                .findByCompanionAndSubject(companionId, subjectId)
                .map(CompanionRelationship::familiarity).orElse(BigDecimal.ZERO);
        return joy.multiply(new BigDecimal("0.40"))
                .add(familiarity.multiply(new BigDecimal("0.60")))
                .setScale(2, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO).min(HUNDRED);
    }
}
