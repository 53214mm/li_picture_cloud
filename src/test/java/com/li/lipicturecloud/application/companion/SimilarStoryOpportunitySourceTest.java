package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.CompanionMoodRepository;
import com.li.lipicturecloud.domain.companion.CompanionMoodRules;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRepository;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;
import com.li.lipicturecloud.domain.picture.PictureAsset;
import com.li.lipicturecloud.domain.picture.PictureAssetRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimilarStoryOpportunitySourceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");

    private GrowthRecordRepository growthRepository;
    private PictureAssetRepository pictureRepository;
    private SpaceAuthorizationAccessService authorization;
    private SimilarStoryOpportunitySource source;

    @BeforeEach
    void setUp() {
        growthRepository = mock(GrowthRecordRepository.class);
        pictureRepository = mock(PictureAssetRepository.class);
        authorization = mock(SpaceAuthorizationAccessService.class);
        ProposalOpportunityEvaluator evaluator = new ProposalOpportunityEvaluator(
                mock(CompanionMoodRepository.class), mock(CompanionRelationshipRepository.class),
                CompanionMoodRules.v1(), Clock.fixed(NOW, ZoneOffset.UTC));
        source = new SimilarStoryOpportunitySource(growthRepository, pictureRepository,
                authorization, evaluator);
    }

    @Test
    void proposesWhenFedPicturesSpaceGainedMorePictures() {
        when(growthRepository.findRecentFedPictureIds(11L, 5)).thenReturn(List.of(101L, 102L));
        when(pictureRepository.findAssetById(101L)).thenReturn(Optional.empty());
        when(pictureRepository.findAssetById(102L))
                .thenReturn(Optional.of(new PictureAsset(102L, 7L, 30L)));
        when(pictureRepository.countRecentInSpace(30L, NOW.minus(java.time.Duration.ofDays(7))))
                .thenReturn(4L);

        Optional<ProposalOpportunity> opportunity = materialize(11L, 7L);

        assertThat(opportunity).isPresent();
        assertThat(opportunity.get().type()).isEqualTo(ProposalOpportunityType.SIMILAR_STORY);
        assertThat(opportunity.get().content()).contains("4 张图片");
        verify(authorization).checkForUser(PICTURE_VIEW, 102L, 7L);
    }

    @Test
    void skipsUnavailablePicturesAndPublicOnes() {
        when(growthRepository.findRecentFedPictureIds(11L, 5)).thenReturn(List.of(101L, 102L));
        when(pictureRepository.findAssetById(101L)).thenReturn(Optional.empty());
        // 公共图片 spaceId 为 null，不应触发空间计数。
        when(pictureRepository.findAssetById(102L))
                .thenReturn(Optional.of(new PictureAsset(102L, 7L, null)));

        Optional<ProposalOpportunity> opportunity = materialize(11L, 7L);

        assertThat(opportunity).isEmpty();
        verify(pictureRepository, never()).countRecentInSpace(anyLong(), any());
        verify(authorization, never()).checkForUser(any(), any(), any());
    }

    @Test
    void observeOnlyConfirmsFedPicturesWithoutPictureTableAccess() {
        when(growthRepository.findRecentFedPictureIds(11L, 5)).thenReturn(List.of(101L));

        Optional<OpportunityObservation> observation = source.observe(11L, 7L, NOW);

        assertThat(observation).isPresent();
        assertThat(observation.get().type()).isEqualTo(ProposalOpportunityType.SIMILAR_STORY);
        // 守门前的观察不碰图片表、不校验授权、不统计空间。
        verify(pictureRepository, never()).findAssetById(anyLong());
        verify(pictureRepository, never()).countRecentInSpace(anyLong(), any());
        verify(authorization, never()).checkForUser(any(), any(), any());
    }

    @Test
    void staysQuietWhenSpaceHasOnlyTheFedPicture() {
        when(growthRepository.findRecentFedPictureIds(11L, 5)).thenReturn(List.of(101L));
        when(pictureRepository.findAssetById(101L))
                .thenReturn(Optional.of(new PictureAsset(101L, 7L, 30L)));
        when(pictureRepository.countRecentInSpace(30L, NOW.minus(java.time.Duration.ofDays(7))))
                .thenReturn(1L);

        Optional<ProposalOpportunity> opportunity = materialize(11L, 7L);

        assertThat(opportunity).isEmpty();
    }

    @Test
    void staysQuietWithoutAnyFedPictures() {
        when(growthRepository.findRecentFedPictureIds(11L, 5)).thenReturn(List.of());

        assertThat(source.observe(11L, 7L, NOW)).isEmpty();
        verify(pictureRepository, never()).findAssetById(anyLong());
    }

    @Test
    void keepsScanningWhenTheFirstFedPicturesSpaceIsQuiet() {
        // 第一张喂养图的空间只有 1 张（安静），第二张的空间最近有 3 张 → 仍应产生提案。
        when(growthRepository.findRecentFedPictureIds(11L, 5)).thenReturn(List.of(101L, 102L));
        when(pictureRepository.findAssetById(101L))
                .thenReturn(Optional.of(new PictureAsset(101L, 7L, 30L)));
        when(pictureRepository.countRecentInSpace(30L, NOW.minus(java.time.Duration.ofDays(7))))
                .thenReturn(1L);
        when(pictureRepository.findAssetById(102L))
                .thenReturn(Optional.of(new PictureAsset(102L, 7L, 31L)));
        when(pictureRepository.countRecentInSpace(31L, NOW.minus(java.time.Duration.ofDays(7))))
                .thenReturn(3L);

        Optional<ProposalOpportunity> opportunity = materialize(11L, 7L);

        assertThat(opportunity).isPresent();
        assertThat(opportunity.get().content()).contains("3 张图片");
    }

    @Test
    void revokedPictureSpaceNeverLeaksItsRecentCount() {
        // 用户从团队空间被移出后（或图片被撤回），喂养记录仍在：不得再声称"那个空间
        // 最近又攒下了 N 张图片"，也不得执行空间计数。
        when(growthRepository.findRecentFedPictureIds(11L, 5)).thenReturn(List.of(102L));
        when(pictureRepository.findAssetById(102L))
                .thenReturn(Optional.of(new PictureAsset(102L, 7L, 30L)));
        doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "缺少权限"))
                .when(authorization).checkForUser(PICTURE_VIEW, 102L, 7L);

        Optional<ProposalOpportunity> opportunity = materialize(11L, 7L);

        assertThat(opportunity).isEmpty();
        verify(pictureRepository, never()).countRecentInSpace(anyLong(), any());
    }

    @Test
    void deletedPictureIsSkippedWhileLaterPicturesStillApply() {
        when(growthRepository.findRecentFedPictureIds(11L, 5)).thenReturn(List.of(101L, 102L));
        when(pictureRepository.findAssetById(101L))
                .thenReturn(Optional.of(new PictureAsset(101L, 7L, 30L)));
        doThrow(new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"))
                .when(authorization).checkForUser(PICTURE_VIEW, 101L, 7L);
        when(pictureRepository.findAssetById(102L))
                .thenReturn(Optional.of(new PictureAsset(102L, 7L, 31L)));
        when(pictureRepository.countRecentInSpace(31L, NOW.minus(java.time.Duration.ofDays(7))))
                .thenReturn(3L);

        Optional<ProposalOpportunity> opportunity = materialize(11L, 7L);

        assertThat(opportunity).isPresent();
        assertThat(opportunity.get().content()).contains("3 张图片");
        verify(authorization).checkForUser(PICTURE_VIEW, 101L, 7L);
        verify(authorization).checkForUser(PICTURE_VIEW, 102L, 7L);
    }

    @Test
    void authorizationOutageKeepsTheWholeSourceQuietFailClosed() {
        when(growthRepository.findRecentFedPictureIds(11L, 5)).thenReturn(List.of(102L));
        when(pictureRepository.findAssetById(102L))
                .thenReturn(Optional.of(new PictureAsset(102L, 7L, 30L)));
        doThrow(new BusinessException(ErrorCode.SYSTEM_ERROR, "授权服务暂时不可用"))
                .when(authorization).checkForUser(PICTURE_VIEW, 102L, 7L);

        Optional<ProposalOpportunity> opportunity = materialize(11L, 7L);

        // fail-closed：授权无法验证时不产出任何候选，绝不在无权限确认时告诉用户空间动态。
        assertThat(opportunity).isEmpty();
        verify(pictureRepository, never()).countRecentInSpace(anyLong(), any());
    }

    private Optional<ProposalOpportunity> materialize(long companionId, long subjectId) {
        return source.observe(companionId, subjectId, NOW)
                .flatMap(observation -> source.materialize(observation, companionId, subjectId, NOW));
    }
}
