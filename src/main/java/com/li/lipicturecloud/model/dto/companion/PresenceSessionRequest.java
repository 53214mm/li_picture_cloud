package com.li.lipicturecloud.model.dto.companion;

/** Explicit opt-in; no owner or arbitrary page content is accepted. */
public record PresenceSessionRequest(Boolean enabled, String mode, Integer maxDecisions,
                                     Boolean muted, String intensity, String scope, Integer cooldownSeconds) {
    public PresenceSessionRequest(Boolean enabled, String mode, Integer maxDecisions, Boolean muted, String intensity) {
        this(enabled, mode, maxDecisions, muted, intensity, null, null);
    }
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String key, Object value) {
        throw new IllegalArgumentException("Unknown presence request field");
    }
}
