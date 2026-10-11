package com.li.lipicturecloud.application.companion.presence;

import com.li.lipicturecloud.domain.airuntime.ModelProvider;

/** Safe configuration summary, not a provider health probe. No credential or endpoint fields. */
public record PresenceAvailabilityView(boolean enabled, boolean realEnabled, boolean available,
                                       String reason, ConnectionSummary connection, boolean rehearsalEnabled,
                                       int rehearsalDailyLimit, Integer rehearsalDailyRemaining) {
    public PresenceAvailabilityView(boolean enabled, boolean realEnabled, boolean available, String reason, ConnectionSummary connection) {
        this(enabled, realEnabled, available, reason, connection, false, 24, null);
    }
    public record ConnectionSummary(ModelProvider provider, String modelCode) {}
}
