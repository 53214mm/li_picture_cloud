package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;
import com.li.lipicturecloud.domain.picture.PictureAsset;
import com.li.lipicturecloud.domain.picture.PictureAssetRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 相似图片故事机会：最近完整喂养过的图片所属空间，最近 7 天又出现其他图片时产生。
 *
 * <p>只读取图片的空间归属与计数，不读取图片内容；空间不存在或图片已不可用则静默跳过。
 * 冲动得分固定偏低（50 分位），让每周回顾与纪念日优先。</p>
 */
@Component
public class SimilarStoryOpportunitySource implements CompanionOpportunitySource {

    private static final int RECENT_FED_SCAN = 5;
    private static final BigDecimal IMPULSE = new BigDecimal("50.00");

    private final GrowthRecordRepository growthRepository;
    private final PictureAssetRepository pictureRepository;

    public SimilarStoryOpportunitySource(GrowthRecordRepository growthRepository,
                                         PictureAssetRepository pictureRepository) {
        this.growthRepository = growthRepository;
        this.pictureRepository = pictureRepository;
    }

    @Override
    public Optional<ProposalOpportunity> findOpportunity(long companionId, long subjectId, Instant now) {
        List<Long> fedPictureIds = growthRepository.findRecentFedPictureIds(companionId, RECENT_FED_SCAN);
        for (Long pictureId : fedPictureIds) {
            Long spaceId = pictureRepository.findAssetById(pictureId)
                    .map(PictureAsset::spaceId)
                    .orElse(null);
            if (spaceId == null) {
                continue;
            }
            long recent = pictureRepository.countRecentInSpace(spaceId, now.minus(Duration.ofDays(7)));
            // 至少两张（包含喂过的那张之外还有别的），才值得提议"看看新的像不像"。
            if (recent < 2) {
                return Optional.empty();
            }
            String content = String.format(
                    "你最近喂我图片的那个空间，最近又攒下了 %d 张图片。要我看看它们和上次那张像不像吗？",
                    recent);
            return Optional.of(new ProposalOpportunity(ProposalOpportunityType.SIMILAR_STORY,
                    IMPULSE, content));
        }
        return Optional.empty();
    }
}
