package com.li.lipicturecloud.domain.companion;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 自主契约守门：顺序固定，任一不过即丢弃提案。
 *
 * <p>总开关 → 频率上限 → 安静时段 → 上次提案间隔。守门失败返回原因码，
 * 只写入指标，不打扰用户。</p>
 */
public final class ProposalGate {

    public static final String CONTRACT_DISABLED = "CONTRACT_DISABLED";
    public static final String FREQUENCY_ZERO = "FREQUENCY_ZERO";
    public static final String QUIET_HOURS = "QUIET_HOURS";
    public static final String FREQUENCY_BUDGET = "FREQUENCY_BUDGET";
    public static final String PASSED = "PASSED";

    private ProposalGate() {
    }

    public static GateResult check(CompanionAutonomyContract contract, Instant now, ZoneId zone,
                                   Instant lastProposalTime) {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(zone, "zone");
        if (!contract.active()) {
            return new GateResult(false, CONTRACT_DISABLED);
        }
        if (contract.maxFrequencyHours() == 0) {
            return new GateResult(false, FREQUENCY_ZERO);
        }
        LocalTime time = now.atZone(zone).toLocalTime();
        if (inQuietHours(contract, time)) {
            return new GateResult(false, QUIET_HOURS);
        }
        if (lastProposalTime != null
                && now.isBefore(lastProposalTime.plus(Duration.ofHours(contract.maxFrequencyHours())))) {
            return new GateResult(false, FREQUENCY_BUDGET);
        }
        return new GateResult(true, PASSED);
    }

    /** 安静时段支持跨午夜（如 23:00-08:00）；起止相同表示不设安静时段。 */
    static boolean inQuietHours(CompanionAutonomyContract contract, LocalTime time) {
        LocalTime start = contract.quietStart();
        LocalTime end = contract.quietEnd();
        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !time.isBefore(start) && time.isBefore(end);
        }
        return !time.isBefore(start) || time.isBefore(end);
    }

    public record GateResult(boolean passed, String reasonCode) {
        public GateResult {
            Objects.requireNonNull(reasonCode, "reasonCode");
        }
    }
}
