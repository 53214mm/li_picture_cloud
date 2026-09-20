package com.li.lipicturecloud.application.companion.view;

import java.time.LocalTime;

/**
 * 自主契约的展示视图。
 */
public record CompanionContractView(
        boolean active,
        LocalTime quietStart,
        LocalTime quietEnd,
        int maxFrequencyHours,
        long revision) {
}
