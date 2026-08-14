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
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 每周影像回顾机会：过去 7 天至少喂养一次时产生，冲动得分来自当前情绪与关系。
 * 机会源优先级第 1（每周回顾优先于纪念日与相似图片）。
 */
@Component
@org.springframework.core.annotation.Order(1)
public class WeeklyReviewOpportunitySource implements CompanionOpportunitySource {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private final GrowthRecordRepository growthRepository;
    private final CompanionMoodRepository moodRepository;
    private final CompanionRelationshipRepository relationshipRepository;

    public WeeklyReviewOpportunitySource(GrowthRecordRepository growthRepository,
                                         CompanionMoodRepository moodRepository,
                                         CompanionRelationshipRepository relationshipRepository) {
        this.growthRepository = growthRepository;
        this.moodRepository = moodRepository;
        this.relationshipRepository = relationshipRepository;
    }

    @Override
    public Optional<ProposalOpportunity> findOpportunity(long companionId, long subjectId, Instant now) {
        long feeds = growthRepository.countSince(companionId, now.minus(Duration.ofDays(7)));
        if (feeds < 1) {
            return Optional.empty();
        }
        String content = String.format("这周你喂了我 %d 次。想听我讲一段我们的故事吗？", feeds);
        return Optional.of(new ProposalOpportunity(ProposalOpportunityType.WEEKLY_REVIEW,
                impulseScore(companionId, subjectId), content));
    }

    private BigDecimal impulseScore(long companionId, long subjectId) {
        BigDecimal joy = moodRepository.findByCompanionId(companionId)
                .map(CompanionMood::joy).orElse(BigDecimal.ZERO);
        BigDecimal familiarity = relationshipRepository
                .findByCompanionAndSubject(companionId, subjectId)
                .map(CompanionRelationship::familiarity).orElse(BigDecimal.ZERO);
        return joy.multiply(new BigDecimal("0.60"))
                .add(familiarity.multiply(new BigDecimal("0.40")))
                .setScale(2, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO).min(HUNDRED);
    }
}
