package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanionRelationshipTest {

    private final CompanionRelationshipRules rules = CompanionRelationshipRules.v1();

    @Test
    void startsNeutralForASubject() {
        CompanionRelationship relationship = CompanionRelationship.initial(11L, 7L);

        assertThat(relationship.familiarity()).isEqualByComparingTo("0.00");
        assertThat(relationship.recentFeedback()).isEqualByComparingTo("0.00");
        assertThat(relationship.revision()).isZero();
    }

    @Test
    void fullFeedImpactAccumulatesSlowly() {
        CompanionRelationship relationship = CompanionRelationship.initial(11L, 7L);

        CompanionRelationship after = relationship.apply(rules.fullFeedImpact(), rules);

        assertThat(after.familiarity()).isEqualByComparingTo("5.00");
        assertThat(after.trust()).isEqualByComparingTo("2.00");
        assertThat(after.closeness()).isEqualByComparingTo("1.00");
        assertThat(after.tacit()).isEqualByComparingTo("1.00");
        assertThat(after.recentFeedback()).isEqualByComparingTo("5.00");
        assertThat(after.revision()).isEqualTo(1L);
    }

    @Test
    void revisitImpactTouchesOnlyFamiliarityTacitAndFeedback() {
        CompanionRelationship relationship = CompanionRelationship.initial(11L, 7L);

        CompanionRelationship after = relationship.apply(rules.revisitImpact(), rules);

        assertThat(after.familiarity()).isEqualByComparingTo("2.00");
        assertThat(after.trust()).isEqualByComparingTo("0.00");
        assertThat(after.closeness()).isEqualByComparingTo("0.00");
        assertThat(after.tacit()).isEqualByComparingTo("1.00");
        assertThat(after.recentFeedback()).isEqualByComparingTo("2.00");
    }

    @Test
    void positiveAxesNeverGoNegativeAndCapAtHundred() {
        CompanionRelationship relationship = CompanionRelationship.initial(11L, 7L);

        CompanionRelationship negativeImpact = relationship.apply(new RelationshipImpact(
                bd("-50.00"), bd("-50.00"), bd("-50.00"), bd("-50.00"), bd("-10.00")), rules);
        assertThat(negativeImpact.familiarity()).isEqualByComparingTo("0.00");
        assertThat(negativeImpact.recentFeedback()).isEqualByComparingTo("-10.00");

        CompanionRelationship high = new CompanionRelationship(null, 11L, 7L,
                bd("95.00"), bd("95.00"), bd("95.00"), bd("95.00"), bd("0.00"), 2L);
        CompanionRelationship capped = high.apply(rules.fullFeedImpact(), rules);
        assertThat(capped.familiarity()).isEqualByComparingTo("100.00");
        assertThat(capped.trust()).isEqualByComparingTo("97.00");
    }

    @Test
    void singleAxisMovementIsCappedByRules() {
        CompanionRelationship relationship = CompanionRelationship.initial(11L, 7L);

        CompanionRelationship after = relationship.apply(new RelationshipImpact(
                bd("50.00"), bd("0.00"), bd("0.00"), bd("0.00"), bd("0.00")), rules);

        assertThat(after.familiarity()).isEqualByComparingTo("10.00");
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertThatThrownBy(() -> new CompanionRelationship(null, 11L, 7L,
                bd("101.00"), bd("0.00"), bd("0.00"), bd("0.00"), bd("0.00"), 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompanionRelationship(null, 11L, 7L,
                bd("0.00"), bd("0.00"), bd("0.00"), bd("0.00"), bd("-100.01"), 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeRecentFeedbackMovesBelowZero() {
        CompanionRelationship relationship = CompanionRelationship.initial(11L, 7L);

        CompanionRelationship after = relationship.apply(new RelationshipImpact(
                bd("0.00"), bd("0.00"), bd("0.00"), bd("0.00"), bd("-8.00")), rules);

        assertThat(after.recentFeedback()).isEqualByComparingTo("-8.00");
    }

    @Test
    void recentFeedbackIsCappedAtHundredInBothDirections() {
        CompanionRelationship high = new CompanionRelationship(null, 11L, 7L,
                bd("0.00"), bd("0.00"), bd("0.00"), bd("0.00"), bd("95.00"), 2L);
        assertThat(high.apply(rules.fullFeedImpact(), rules).recentFeedback())
                .isEqualByComparingTo("100.00");

        CompanionRelationship low = new CompanionRelationship(null, 11L, 7L,
                bd("0.00"), bd("0.00"), bd("0.00"), bd("0.00"), bd("-95.00"), 2L);
        assertThat(low.apply(new RelationshipImpact(
                bd("0.00"), bd("0.00"), bd("0.00"), bd("0.00"), bd("-10.00")), rules).recentFeedback())
                .isEqualByComparingTo("-100.00");
    }

    @Test
    void withIdAndRestoreGuardTheirTransitions() {
        CompanionRelationship relationship = CompanionRelationship.initial(11L, 7L);

        assertThat(relationship.withId(51L).id()).isEqualTo(51L);
        assertThatThrownBy(() -> relationship.withId(0L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CompanionRelationship.restore(0L, 11L, 7L,
                bd("0"), bd("0"), bd("0"), bd("0"), bd("0"), 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompanionRelationship(-1L, 11L, 7L,
                bd("0"), bd("0"), bd("0"), bd("0"), bd("0"), 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionRelationship.initial(0L, 7L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restoreRebuildsAPersistedRelationship() {
        CompanionRelationship restored = CompanionRelationship.restore(51L, 11L, 7L,
                bd("12.00"), bd("4.50"), bd("2.00"), bd("1.00"), bd("-3.00"), 7L);

        assertThat(restored.id()).isEqualTo(51L);
        assertThat(restored.familiarity()).isEqualByComparingTo("12.00");
        assertThat(restored.trust()).isEqualByComparingTo("4.50");
        assertThat(restored.recentFeedback()).isEqualByComparingTo("-3.00");
        assertThat(restored.revision()).isEqualTo(7L);
    }

    @Test
    void positiveAxesRejectNegativeRestoredValues() {
        assertThatThrownBy(() -> CompanionRelationship.restore(51L, 11L, 7L,
                bd("-0.01"), bd("0"), bd("0"), bd("0"), bd("0"), 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionRelationship.restore(51L, 11L, 7L,
                bd("0"), bd("0"), bd("0"), bd("-100.01"), bd("0"), 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
