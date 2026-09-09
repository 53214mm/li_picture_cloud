package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 纪念日机会：往年同月同日（上海日历）至少完整喂养过一次时产生。
 *
 * <p>只依赖成长记录（不查询图片表），文案确定性生成；冲动得分由"当前情绪 + 关系"评估。
 * 机会源优先级第 2（次于每周回顾，先于相似图片）。</p>
 */
@Component
@org.springframework.core.annotation.Order(2)
public class AnniversaryOpportunitySource implements CompanionOpportunitySource {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal JOY_WEIGHT = new BigDecimal("0.40");
    private static final BigDecimal FAMILIARITY_WEIGHT = new BigDecimal("0.60");

    private final GrowthRecordRepository growthRepository;
    private final ProposalOpportunityEvaluator evaluator;

    public AnniversaryOpportunitySource(GrowthRecordRepository growthRepository,
                                        ProposalOpportunityEvaluator evaluator) {
        this.growthRepository = growthRepository;
        this.evaluator = evaluator;
    }

    @Override
    public ProposalOpportunityType type() {
        return ProposalOpportunityType.ANNIVERSARY;
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
                evaluator.score(companionId, subjectId, JOY_WEIGHT, FAMILIARITY_WEIGHT), content));
    }
}
