package com.li.lipicturecloud.application.companion.presence;

import com.li.lipicturecloud.domain.airuntime.ModelProvider;

/** Safe configuration summary, not a provider health probe. No credential or endpoint fields. */
public record PresenceAvailabilityView(boolean enabled, boolean realEnabled, boolean available,
                                       String reason, ConnectionSummary connection) {
    public record ConnectionSummary(ModelProvider provider, String modelCode) {}
}
