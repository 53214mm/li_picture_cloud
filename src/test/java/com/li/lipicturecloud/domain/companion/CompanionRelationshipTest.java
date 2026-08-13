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

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
