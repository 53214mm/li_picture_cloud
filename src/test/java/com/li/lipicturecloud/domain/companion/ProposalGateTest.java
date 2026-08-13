package com.li.lipicturecloud.domain.companion;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ProposalGateTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Instant DAYTIME = Instant.parse("2026-08-14T02:00:00Z"); // 上海 10:00

    @Test
    void passesWhenContractAllowsAndBudgetHasElapsed() {
        CompanionAutonomyContract contract = new CompanionAutonomyContract(null, 11L, 7L, true,
                LocalTime.of(23, 0), LocalTime.of(8, 0), 72, 0L);

        ProposalGate.GateResult result = ProposalGate.check(contract, DAYTIME, SHANGHAI,
                DAYTIME.minusSeconds(73 * 3600L));

        assertThat(result.passed()).isTrue();
        assertThat(result.reasonCode()).isEqualTo(ProposalGate.PASSED);
    }

    @Test
    void disabledContractBlocksEverything() {
        CompanionAutonomyContract contract = new CompanionAutonomyContract(null, 11L, 7L, false,
                LocalTime.of(23, 0), LocalTime.of(8, 0), 72, 0L);

        ProposalGate.GateResult result = ProposalGate.check(contract, DAYTIME, SHANGHAI, null);

        assertThat(result.passed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(ProposalGate.CONTRACT_DISABLED);
    }

    @Test
    void zeroFrequencyMeansFullyOff() {
        CompanionAutonomyContract contract = new CompanionAutonomyContract(null, 11L, 7L, true,
                LocalTime.of(23, 0), LocalTime.of(8, 0), 0, 0L);

        assertThat(ProposalGate.check(contract, DAYTIME, SHANGHAI, null).reasonCode())
                .isEqualTo(ProposalGate.FREQUENCY_ZERO);
    }

    @Test
    void quietHoursBlockBothDaytimeAndOvernightWindows() {
        CompanionAutonomyContract contract = new CompanionAutonomyContract(null, 11L, 7L, true,
                LocalTime.of(23, 0), LocalTime.of(8, 0), 72, 0L);
        // 上海 23:30 = 15:30Z
        Instant lateNight = Instant.parse("2026-08-14T15:30:00Z");
        // 上海 02:00 = 前一日 18:00Z
        Instant earlyMorning = Instant.parse("2026-08-13T18:00:00Z");

        assertThat(ProposalGate.check(contract, lateNight, SHANGHAI, null).reasonCode())
                .isEqualTo(ProposalGate.QUIET_HOURS);
        assertThat(ProposalGate.check(contract, earlyMorning, SHANGHAI, null).reasonCode())
                .isEqualTo(ProposalGate.QUIET_HOURS);
    }

    @Test
    void frequencyBudgetBlocksRecentProposals() {
        CompanionAutonomyContract contract = new CompanionAutonomyContract(null, 11L, 7L, true,
                LocalTime.of(23, 0), LocalTime.of(8, 0), 72, 0L);

        ProposalGate.GateResult result = ProposalGate.check(contract, DAYTIME, SHANGHAI,
                DAYTIME.minusSeconds(3600L));

        assertThat(result.passed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(ProposalGate.FREQUENCY_BUDGET);
    }

    @Test
    void equalQuietBoundsMeanNoQuietHours() {
        CompanionAutonomyContract contract = new CompanionAutonomyContract(null, 11L, 7L, true,
                LocalTime.NOON, LocalTime.NOON, 72, 0L);

        ProposalGate.GateResult result = ProposalGate.check(contract, DAYTIME, SHANGHAI, null);

        assertThat(result.passed()).isTrue();
    }
}
