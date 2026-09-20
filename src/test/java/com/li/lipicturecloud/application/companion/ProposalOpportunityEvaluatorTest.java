package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.CompanionMood;
import com.li.lipicturecloud.domain.companion.CompanionMoodRepository;
import com.li.lipicturecloud.domain.companion.CompanionMoodRules;
import com.li.lipicturecloud.domain.companion.CompanionRelationship;
import com.li.lipicturecloud.domain.companion.CompanionRelationshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProposalOpportunityEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");

    private CompanionMoodRepository moodRepository;
    private CompanionRelationshipRepository relationshipRepository;
    private ProposalOpportunityEvaluator evaluator;

    @BeforeEach
    void setUp() {
        moodRepository = mock(CompanionMoodRepository.class);
        relationshipRepository = mock(CompanionRelationshipRepository.class);
        evaluator = new ProposalOpportunityEvaluator(moodRepository, relationshipRepository,
                CompanionMoodRules.v1(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void scoreUsesDecayedCurrentMoodAndFamiliarityWithTheSceneWeights() {
        // 两小时前写入愉悦 30：衰减后应为 20（每小时 -5），再按权重与熟悉度合成。
        CompanionMood stale = new CompanionMood(51L, 11L,
                bd("0.00"), bd("30.00"), bd("0.00"), bd("0.00"), bd("0.00"),
                2L, NOW.minusSeconds(2 * 3600L));
        when(moodRepository.findByCompanionId(11L)).thenReturn(Optional.of(stale));
        when(moodRepository.save(any(), anyLong())).thenReturn(true);
        when(relationshipRepository.findByCompanionAndSubject(11L, 7L))
                .thenReturn(Optional.of(CompanionRelationship.restore(61L, 11L, 7L,
                        bd("10"), bd("0"), bd("0"), bd("0"), bd("0"), 1L)));

        BigDecimal score = evaluator.score(11L, 7L, bd("0.50"), bd("0.50"));

        assertThat(score).isEqualByComparingTo("15.00");
        // 衰减后的情绪被 CAS 写回（revision 2 → 3）。
        ArgumentCaptor<CompanionMood> saved = ArgumentCaptor.forClass(CompanionMood.class);
        verify(moodRepository).save(saved.capture(), org.mockito.ArgumentMatchers.eq(2L));
        assertThat(saved.getValue().joy()).isEqualByComparingTo("20.00");
    }

    @Test
    void scoreIsZeroWithoutAnyMoodOrRelationship() {
        when(moodRepository.findByCompanionId(11L)).thenReturn(Optional.empty());
        when(relationshipRepository.findByCompanionAndSubject(11L, 7L)).thenReturn(Optional.empty());

        BigDecimal score = evaluator.score(11L, 7L, bd("0.60"), bd("0.40"));

        assertThat(score).isEqualByComparingTo("0.00");
        verify(moodRepository, org.mockito.Mockito.never()).save(any(), anyLong());
    }

    @Test
    void decayWriteConflictStillScoresWithTheDecayedValue() {
        CompanionMood stale = new CompanionMood(51L, 11L,
                bd("0.00"), bd("30.00"), bd("0.00"), bd("0.00"), bd("0.00"),
                2L, NOW.minusSeconds(2 * 3600L));
        when(moodRepository.findByCompanionId(11L)).thenReturn(Optional.of(stale));
        when(moodRepository.save(any(), anyLong())).thenReturn(false);
        when(relationshipRepository.findByCompanionAndSubject(11L, 7L))
                .thenReturn(Optional.of(CompanionRelationship.restore(61L, 11L, 7L,
                        bd("10"), bd("0"), bd("0"), bd("0"), bd("0"), 1L)));

        BigDecimal score = evaluator.score(11L, 7L, bd("0.50"), bd("0.50"));

        assertThat(score).isEqualByComparingTo("15.00");
    }

    @Test
    void onlyPositiveAccumulationReachesTheProposalThreshold() {
        assertThat(evaluator.reachesProposalThreshold(bd("0.00"))).isFalse();
        assertThat(evaluator.reachesProposalThreshold(bd("-0.01"))).isFalse();
        assertThat(evaluator.reachesProposalThreshold(bd("0.01"))).isTrue();
        assertThat(evaluator.reachesProposalThreshold(bd("100.00"))).isTrue();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
