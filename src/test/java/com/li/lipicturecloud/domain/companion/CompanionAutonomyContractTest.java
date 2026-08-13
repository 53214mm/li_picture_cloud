package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanionAutonomyContractTest {

    @Test
    void initialContractIsOffWithConservativeDefaults() {
        CompanionAutonomyContract contract = CompanionAutonomyContract.initial(11L, 7L);

        assertThat(contract.active()).isFalse();
        assertThat(contract.quietStart()).isEqualTo(LocalTime.of(23, 0));
        assertThat(contract.quietEnd()).isEqualTo(LocalTime.of(8, 0));
        assertThat(contract.maxFrequencyHours()).isEqualTo(72);
        assertThat(contract.revision()).isZero();
    }

    @Test
    void updatedAdvancesRevisionAndValidatesBounds() {
        CompanionAutonomyContract contract = CompanionAutonomyContract.initial(11L, 7L);

        CompanionAutonomyContract updated = contract.updated(true, LocalTime.of(22, 0),
                LocalTime.of(7, 0), 24);

        assertThat(updated.active()).isTrue();
        assertThat(updated.quietStart()).isEqualTo(LocalTime.of(22, 0));
        assertThat(updated.maxFrequencyHours()).isEqualTo(24);
        assertThat(updated.revision()).isEqualTo(1L);

        assertThatThrownBy(() -> contract.updated(true, LocalTime.NOON, LocalTime.NOON, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> contract.updated(true, LocalTime.NOON, LocalTime.NOON, 721))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> contract.updated(true, null, LocalTime.NOON, 24))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void restoreRebuildsPersistedContract() {
        CompanionAutonomyContract restored = CompanionAutonomyContract.restore(51L, 11L, 7L, true,
                LocalTime.of(22, 30), LocalTime.of(6, 30), 48, 3L);

        assertThat(restored.id()).isEqualTo(51L);
        assertThat(restored.active()).isTrue();
        assertThat(restored.quietStart()).isEqualTo(LocalTime.of(22, 30));
        assertThat(restored.maxFrequencyHours()).isEqualTo(48);
        assertThat(restored.revision()).isEqualTo(3L);
        assertThatThrownBy(() -> CompanionAutonomyContract.restore(0L, 11L, 7L, false,
                LocalTime.NOON, LocalTime.NOON, 72, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsInvalidIdentityAndNulls() {
        assertThatThrownBy(() -> new CompanionAutonomyContract(-1L, 11L, 7L, false,
                LocalTime.NOON, LocalTime.NOON, 72, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompanionAutonomyContract(null, 0L, 7L, false,
                LocalTime.NOON, LocalTime.NOON, 72, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompanionAutonomyContract(null, 11L, 0L, false,
                LocalTime.NOON, LocalTime.NOON, 72, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompanionAutonomyContract(null, 11L, 7L, false,
                null, LocalTime.NOON, 72, 0L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CompanionAutonomyContract(null, 11L, 7L, false,
                LocalTime.NOON, null, 72, 0L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CompanionAutonomyContract(null, 11L, 7L, false,
                LocalTime.NOON, LocalTime.NOON, -1, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionAutonomyContract.initial(0L, 7L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionAutonomyContract.initial(11L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withIdGuardsItsTransition() {
        CompanionAutonomyContract contract = CompanionAutonomyContract.initial(11L, 7L);

        assertThat(contract.withId(51L).id()).isEqualTo(51L);
        assertThatThrownBy(() -> contract.withId(0L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> contract.withId(51L).withId(52L)).isInstanceOf(IllegalStateException.class);
    }
}
