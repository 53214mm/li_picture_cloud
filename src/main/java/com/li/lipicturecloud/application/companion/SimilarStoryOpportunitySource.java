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
import java.util.Objects;
import java.util.Optional;

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;

/**
 * 相似图片故事机会：最近完整喂养过的图片所属空间，最近 7 天又出现其他图片时产生。
 *
 * <p>两段式：
 * <ul>
 *   <li>{@code observe}（守门之前，只读）验证"真实机会"——定位最近喂养图并确认它仍存在、
 *       属于某个空间、当前主体仍有查看权限（图片被删/撤权则跳过，授权基础设施异常按
 *       fail-closed 不产生观察）、且该空间近 7 天图片数 ≥ 2；把已确认的
 *       图片/空间/数量作为最小事实放进观察结果。不做评分、不写回情绪、不生成文案。</li>
 *   <li>{@code materialize}（守门通过后）基于观察事实做必要复验（图片仍存在且权限仍有效），
 *       再完成冲动评分与文案。</li>
 * </ul>
 * 只读取图片的空间归属与计数，不读取图片内容；绝不在无法确认权限时向用户声称
 * "那个空间最近又攒下了 N 张图片"。机会源优先级第 3（机会源按 @Order 顺序短路选择）。</p>
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
    public Optional<OpportunityObservation> observe(long companionId, long subjectId, Instant now) {
        List<Long> fedPictureIds = growthRepository.findRecentFedPictureIds(companionId, RECENT_FED_SCAN);
        for (Long pictureId : fedPictureIds) {
            PictureAsset picture = pictureRepository.findAssetById(pictureId).orElse(null);
            if (picture == null || picture.spaceId() == null) {
                continue;
            }
            PictureAccess access = checkPictureAccess(pictureId, subjectId);
            if (access == PictureAccess.UNVERIFIABLE) {
                // 授权服务/基础设施异常：无法确认任何图片的权限（fail-closed，不产生观察）。
                return Optional.empty();
            }
            if (access == PictureAccess.REVOKED_OR_MISSING) {
                // 该图已撤权/不存在：不是真实机会，继续扫描其他喂养图。
                continue;
            }
            long recent = pictureRepository.countRecentInSpace(picture.spaceId(),
                    now.minus(Duration.ofDays(7)));
            // 至少两张（包含喂过的那张之外还有别的），才构成真实机会；
            // 不满足时继续扫描下一张喂养图。
            if (recent < 2) {
                continue;
            }
            return Optional.of(new OpportunityObservation(ProposalOpportunityType.SIMILAR_STORY,
                    pictureId, picture.spaceId(), recent));
        }
        return Optional.empty();
    }

    @Override
    public Optional<ProposalOpportunity> materialize(OpportunityObservation observation,
                                                     long companionId, long subjectId, Instant now) {
        Objects.requireNonNull(observation, "observation");
        if (observation.type() != ProposalOpportunityType.SIMILAR_STORY
                || observation.pictureId() == null || observation.spaceId() == null
                || observation.recentCount() == null) {
            return Optional.empty();
        }
        // 必要复验：观察后图片可能被删除或权限被撤回；文案引用空间数量前再次确认。
        PictureAsset picture = pictureRepository.findAssetById(observation.pictureId()).orElse(null);
        if (picture == null || !Objects.equals(picture.spaceId(), observation.spaceId())) {
            return Optional.empty();
        }
        PictureAccess access = checkPictureAccess(observation.pictureId(), subjectId);
        if (access != PictureAccess.AUTHORIZED) {
            return Optional.empty();
        }
        long recent = observation.recentCount();
        String content = String.format(
                "你最近喂我图片的那个空间，最近又攒下了 %d 张图片。要我看看它们和上次那张像不像吗？",
                recent);
        return Optional.of(new ProposalOpportunity(ProposalOpportunityType.SIMILAR_STORY,
                evaluator.score(companionId, subjectId, JOY_WEIGHT, FAMILIARITY_WEIGHT), content));
    }

    private enum PictureAccess {
        AUTHORIZED,
        REVOKED_OR_MISSING,
        UNVERIFIABLE
    }

    /**
     * 当前主体对这张喂养图的访问状态。真实撤权/不存在返回 {@code REVOKED_OR_MISSING}
     * （跳过该图）；授权服务或基础设施异常返回 {@code UNVERIFIABLE} 并告警（fail-closed：
     * 无法确认权限时不产生观察/候选）。
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
