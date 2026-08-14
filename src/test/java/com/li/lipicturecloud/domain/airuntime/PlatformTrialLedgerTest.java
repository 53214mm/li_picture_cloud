package com.li.lipicturecloud.domain.airuntime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformTrialLedgerTest {

    @Test
    void createStartsWithFullAvailableBalance() {
        PlatformTrialLedger ledger = PlatformTrialLedger.create(7L, 100L);
        assertThat(ledger.balance()).isEqualTo(100L);
        assertThat(ledger.reserved()).isZero();
        assertThat(ledger.available()).isEqualTo(100L);
        assertThat(ledger.revision()).isZero();
    }

    @Test
    void rejectsInvalidStatesAndAmounts() {
        assertThatThrownBy(() -> PlatformTrialLedger.create(0L, 100L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlatformTrialLedger.create(7L, -1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlatformTrialLedger.restore(1L, 7L, 100L, 101L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never be negative");
        PlatformTrialLedger ledger = PlatformTrialLedger.create(7L, 10L).withId(3L);
        assertThatThrownBy(() -> ledger.reserve(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger.settle(-1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger.release(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger.grant(-2L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reserveSettleReleaseAdvanceRevisionByExactlyOne() {
        PlatformTrialLedger ledger = PlatformTrialLedger.create(7L, 10L).withId(3L);

        PlatformTrialLedger reserved = ledger.reserve(4L);
        assertThat(reserved.reserved()).isEqualTo(4L);
        assertThat(reserved.available()).isEqualTo(6L);
        assertThat(reserved.revision()).isEqualTo(1L);

        PlatformTrialLedger settled = reserved.settle(3L);
        assertThat(settled.balance()).isEqualTo(7L);
        assertThat(settled.reserved()).isEqualTo(1L);
        assertThat(settled.revision()).isEqualTo(2L);

        PlatformTrialLedger released = settled.release(1L);
        assertThat(released.balance()).isEqualTo(7L);
        assertThat(released.reserved()).isZero();
        assertThat(released.revision()).isEqualTo(3L);
    }

    @Test
    void reserveNeverAllowsNegativeAvailableBalance() {
        PlatformTrialLedger ledger = PlatformTrialLedger.create(7L, 5L).withId(3L);

        assertThatThrownBy(() -> ledger.reserve(6L))
                .isInstanceOf(InsufficientTrialBalanceException.class)
                .satisfies(error -> {
                    InsufficientTrialBalanceException insufficient =
                            (InsufficientTrialBalanceException) error;
                    assertThat(insufficient.available()).isEqualTo(5L);
                    assertThat(insufficient.requested()).isEqualTo(6L);
                });
        assertThat(ledger.reserve(5L).available()).isZero();
    }

    @Test
    void settleAndReleaseCannotExceedReserved() {
        PlatformTrialLedger ledger = PlatformTrialLedger.create(7L, 10L).withId(3L).reserve(2L);
        assertThatThrownBy(() -> ledger.settle(3L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger.release(3L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void grantAddsBalanceWithoutTouchingReserved() {
        PlatformTrialLedger ledger = PlatformTrialLedger.create(7L, 10L).withId(3L).reserve(2L);
        PlatformTrialLedger granted = ledger.grant(20L);
        assertThat(granted.balance()).isEqualTo(30L);
        assertThat(granted.reserved()).isEqualTo(2L);
        assertThat(granted.available()).isEqualTo(28L);
    }

    @Test
    void withIdAndRestoreGuardPersistedIdentity() {
        PlatformTrialLedger created = PlatformTrialLedger.create(7L, 10L);
        PlatformTrialLedger persisted = created.withId(3L);
        assertThat(persisted.id()).isEqualTo(3L);
        assertThatThrownBy(() -> persisted.withId(4L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> created.withId(0L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PlatformTrialLedger.restore(null, 7L, 10L, 0L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlatformTrialLedger.restore(0L, 7L, 10L, 0L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlatformTrialLedger.restore(3L, 7L, -1L, 0L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void arithmeticOverflowIsRejected() {
        PlatformTrialLedger max = PlatformTrialLedger.restore(3L, 7L, Long.MAX_VALUE, 0L, 0L);
        assertThatThrownBy(() -> max.grant(1L)).isInstanceOf(ArithmeticException.class);

        // revision 推进溢出（余额与预占都合法的前提下，唯一可达的加法溢出路径）。
        PlatformTrialLedger maxRevision = PlatformTrialLedger.restore(3L, 7L, 10L, 0L,
                Long.MAX_VALUE);
        assertThatThrownBy(() -> maxRevision.reserve(1L)).isInstanceOf(ArithmeticException.class);
    }
}
