package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 每周影像回顾机会：过去 7 天至少喂养一次时产生，冲动得分由"当前情绪 + 关系"评估。
 * 机会源优先级第 1（每周回顾优先于纪念日与相似图片）。
 */
@Component
@org.springframework.core.annotation.Order(1)
public class WeeklyReviewOpportunitySource implements CompanionOpportunitySource {

    private static final BigDecimal JOY_WEIGHT = new BigDecimal("0.60");
    private static final BigDecimal FAMILIARITY_WEIGHT = new BigDecimal("0.40");

    private final GrowthRecordRepository growthRepository;
    private final ProposalOpportunityEvaluator evaluator;

    public WeeklyReviewOpportunitySource(GrowthRecordRepository growthRepository,
                                         ProposalOpportunityEvaluator evaluator) {
        this.growthRepository = growthRepository;
        this.evaluator = evaluator;
    }

    @Override
    public ProposalOpportunityType type() {
        return ProposalOpportunityType.WEEKLY_REVIEW;
    }

    @Override
    public Optional<ProposalOpportunity> findOpportunity(long companionId, long subjectId, Instant now) {
        long feeds = growthRepository.countSince(companionId, now.minus(Duration.ofDays(7)));
        if (feeds < 1) {
            return Optional.empty();
        }
        String content = String.format("这周你喂了我 %d 次。想听我讲一段我们的故事吗？", feeds);
        return Optional.of(new ProposalOpportunity(ProposalOpportunityType.WEEKLY_REVIEW,
                evaluator.score(companionId, subjectId, JOY_WEIGHT, FAMILIARITY_WEIGHT), content));
    }
}
