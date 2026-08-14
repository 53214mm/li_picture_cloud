package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.CompanionMoodRepository;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRepository;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnniversaryOpportunitySourceTest {

    // 上海 2026-08-14 10:00 = 02:00Z
    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");

    private GrowthRecordRepository growthRepository;
    private CompanionMoodRepository moodRepository;
    private CompanionRelationshipRepository relationshipRepository;
    private AnniversaryOpportunitySource source;

    @BeforeEach
    void setUp() {
        growthRepository = mock(GrowthRecordRepository.class);
        moodRepository = mock(CompanionMoodRepository.class);
        relationshipRepository = mock(CompanionRelationshipRepository.class);
        source = new AnniversaryOpportunitySource(growthRepository, moodRepository, relationshipRepository);
    }

    @Test
    void proposesWhenFeedsHappenedOnThisDayInPreviousYears() {
        when(growthRepository.countAnniversaryFeeds(11L, 8, 14)).thenReturn(2L);

        Optional<ProposalOpportunity> opportunity = source.findOpportunity(11L, 7L, NOW);

        assertThat(opportunity).isPresent();
        assertThat(opportunity.get().type()).isEqualTo(ProposalOpportunityType.ANNIVERSARY);
        assertThat(opportunity.get().content()).contains("8 月 14 日");
        assertThat(opportunity.get().content()).contains("往年的今天");
        verify(moodRepository).findByCompanionId(11L);
        verify(relationshipRepository).findByCompanionAndSubject(11L, 7L);
    }

    @Test
    void staysQuietWithoutPastFeedsOnThisDay() {
        when(growthRepository.countAnniversaryFeeds(11L, 8, 14)).thenReturn(0L);

        Optional<ProposalOpportunity> opportunity = source.findOpportunity(11L, 7L, NOW);

        assertThat(opportunity).isEmpty();
        verify(moodRepository, never()).findByCompanionId(anyLong());
        verify(relationshipRepository, never()).findByCompanionAndSubject(anyLong(), anyLong());
    }

    @Test
    void usesShanghaiCalendarForTheDay() {
        // 上海 2026-08-14 02:30（跨午夜后）= 前一日 18:30Z，仍算 8 月 14 日。
        Instant lateNight = Instant.parse("2026-08-13T18:30:00Z");
        when(growthRepository.countAnniversaryFeeds(11L, 8, 14)).thenReturn(1L);

        Optional<ProposalOpportunity> opportunity = source.findOpportunity(11L, 7L, lateNight);

        assertThat(opportunity).isPresent();
        verify(growthRepository).countAnniversaryFeeds(11L, 8, 14);
    }
}
