package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;
import com.li.lipicturecloud.domain.picture.PictureAsset;
import com.li.lipicturecloud.domain.picture.PictureAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimilarStoryOpportunitySourceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");

    private GrowthRecordRepository growthRepository;
    private PictureAssetRepository pictureRepository;
    private SimilarStoryOpportunitySource source;

    @BeforeEach
    void setUp() {
        growthRepository = mock(GrowthRecordRepository.class);
        pictureRepository = mock(PictureAssetRepository.class);
        source = new SimilarStoryOpportunitySource(growthRepository, pictureRepository);
    }

    @Test
    void proposesWhenFedPicturesSpaceGainedMorePictures() {
        when(growthRepository.findRecentFedPictureIds(11L, 5)).thenReturn(List.of(101L, 102L));
        when(pictureRepository.findAssetById(101L)).thenReturn(Optional.empty());
        when(pictureRepository.findAssetById(102L))
                .thenReturn(Optional.of(new PictureAsset(102L, 7L, 30L)));
        when(pictureRepository.countRecentInSpace(30L, NOW.minus(java.time.Duration.ofDays(7))))
                .thenReturn(4L);

        Optional<ProposalOpportunity> opportunity = source.findOpportunity(11L, 7L, NOW);

        assertThat(opportunity).isPresent();
        assertThat(opportunity.get().type()).isEqualTo(ProposalOpportunityType.SIMILAR_STORY);
        assertThat(opportunity.get().content()).contains("4 张图片");
    }

    @Test
    void skipsUnavailablePicturesAndPublicOnes() {
        when(growthRepository.findRecentFedPictureIds(11L, 5)).thenReturn(List.of(101L, 102L));
        when(pictureRepository.findAssetById(101L)).thenReturn(Optional.empty());
        // 公共图片 spaceId 为 null，不应触发空间计数。
        when(pictureRepository.findAssetById(102L))
                .thenReturn(Optional.of(new PictureAsset(102L, 7L, null)));

        Optional<ProposalOpportunity> opportunity = source.findOpportunity(11L, 7L, NOW);

        assertThat(opportunity).isEmpty();
        verify(pictureRepository, never()).countRecentInSpace(anyLong(), any());
    }

    @Test
    void staysQuietWhenSpaceHasOnlyTheFedPicture() {
        when(growthRepository.findRecentFedPictureIds(11L, 5)).thenReturn(List.of(101L));
        when(pictureRepository.findAssetById(101L))
                .thenReturn(Optional.of(new PictureAsset(101L, 7L, 30L)));
        when(pictureRepository.countRecentInSpace(30L, NOW.minus(java.time.Duration.ofDays(7))))
                .thenReturn(1L);

        Optional<ProposalOpportunity> opportunity = source.findOpportunity(11L, 7L, NOW);

        assertThat(opportunity).isEmpty();
    }

    @Test
    void staysQuietWithoutAnyFedPictures() {
        when(growthRepository.findRecentFedPictureIds(11L, 5)).thenReturn(List.of());

        assertThat(source.findOpportunity(11L, 7L, NOW)).isEmpty();
        verify(pictureRepository, never()).findAssetById(anyLong());
    }
}
