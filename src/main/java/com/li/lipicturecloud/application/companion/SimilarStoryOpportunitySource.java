package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;
import com.li.lipicturecloud.domain.picture.PictureAsset;
import com.li.lipicturecloud.domain.picture.PictureAssetRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;

/**
 * 相似图片故事机会：最近完整喂养过的图片所属空间，最近 7 天又出现其他图片时产生。
 *
 * <p>只读取图片的空间归属与计数，不读取图片内容。机会源在引用一张喂养图之前必须重新
 * 校验当前主体对该图仍有查看权限（用户可能已被移出团队空间）：真实撤权/图片不存在则
 * 跳过该图继续扫描；授权服务或基础设施异常按 fail-closed 处理——本轮不产出任何候选，
 * 绝不在无法确认权限时向用户声称"那个空间最近又攒下了 N 张图片"。
 * 机会源优先级第 3（机会源按 @Order 顺序短路选择）。</p>
 */
@Component
@org.springframework.core.annotation.Order(3)
public class SimilarStoryOpportunitySource implements CompanionOpportunitySource {

    private static final Logger log = LoggerFactory.getLogger(SimilarStoryOpportunitySource.class);
    private static final int RECENT_FED_SCAN = 5;
    private static final BigDecimal JOY_WEIGHT = new BigDecimal("0.50");
    private static final BigDecimal FAMILIARITY_WEIGHT = new BigDecimal("0.50");

    private final GrowthRecordRepository growthRepository;
    private final PictureAssetRepository pictureRepository;
    private final SpaceAuthorizationAccessService authorization;
    private final ProposalOpportunityEvaluator evaluator;

    public SimilarStoryOpportunitySource(GrowthRecordRepository growthRepository,
                                         PictureAssetRepository pictureRepository,
                                         SpaceAuthorizationAccessService authorization,
                                         ProposalOpportunityEvaluator evaluator) {
        this.growthRepository = growthRepository;
        this.pictureRepository = pictureRepository;
        this.authorization = authorization;
        this.evaluator = evaluator;
    }

    @Override
    public ProposalOpportunityType type() {
        return ProposalOpportunityType.SIMILAR_STORY;
    }

    @Override
    public Optional<ProposalOpportunity> findOpportunity(long companionId, long subjectId, Instant now) {
        List<Long> fedPictureIds = growthRepository.findRecentFedPictureIds(companionId, RECENT_FED_SCAN);
        for (Long pictureId : fedPictureIds) {
            PictureAsset picture = pictureRepository.findAssetById(pictureId).orElse(null);
            if (picture == null || picture.spaceId() == null) {
                continue;
            }
            PictureAccess access = checkPictureAccess(pictureId, subjectId);
            if (access == PictureAccess.UNVERIFIABLE) {
                // 授权服务/基础设施异常：无法确认任何图片的权限，整源本轮不产出（fail-closed）。
                return Optional.empty();
            }
            if (access == PictureAccess.REVOKED_OR_MISSING) {
                // 该图已撤权/不存在：不引用它，继续扫描其他喂养图。
                continue;
            }
            long recent = pictureRepository.countRecentInSpace(picture.spaceId(),
                    now.minus(Duration.ofDays(7)));
            // 至少两张（包含喂过的那张之外还有别的），才值得提议"看看新的像不像"；
            // 不满足时继续扫描下一张喂养图，而不是终止整个机会源。
            if (recent < 2) {
                continue;
            }
            String content = String.format(
                    "你最近喂我图片的那个空间，最近又攒下了 %d 张图片。要我看看它们和上次那张像不像吗？",
                    recent);
            return Optional.of(new ProposalOpportunity(ProposalOpportunityType.SIMILAR_STORY,
                    evaluator.score(companionId, subjectId, JOY_WEIGHT, FAMILIARITY_WEIGHT), content));
        }
        return Optional.empty();
    }

    private enum PictureAccess {
        AUTHORIZED,
        REVOKED_OR_MISSING,
        UNVERIFIABLE
    }

    /**
     * 当前主体对这张喂养图的访问状态。真实撤权/不存在返回 {@code REVOKED_OR_MISSING}
     * （跳过该图）；授权服务或基础设施异常返回 {@code UNVERIFIABLE} 并告警（fail-closed：
     * 无法确认权限时本机会源不产出候选）。
     */
    private PictureAccess checkPictureAccess(Long pictureId, long subjectId) {
        try {
            authorization.checkForUser(PICTURE_VIEW, pictureId, subjectId);
            return PictureAccess.AUTHORIZED;
        } catch (BusinessException error) {
            if (error.getCode() == ErrorCode.NOT_FOUND_ERROR.getCode()
                    || error.getCode() == ErrorCode.NO_AUTH_ERROR.getCode()) {
                log.info("companion_proposal_similar_denied subjectId={} pictureId={} reason=PICTURE_UNAVAILABLE",
                        subjectId, pictureId);
                return PictureAccess.REVOKED_OR_MISSING;
            }
            log.warn("companion_proposal_similar_unverifiable subjectId={} pictureId={} exceptionType={}",
                    subjectId, pictureId, error.getClass().getName());
            return PictureAccess.UNVERIFIABLE;
        } catch (RuntimeException error) {
            log.warn("companion_proposal_similar_unverifiable subjectId={} pictureId={} exceptionType={}",
                    subjectId, pictureId, error.getClass().getName());
            return PictureAccess.UNVERIFIABLE;
        }
    }
}
