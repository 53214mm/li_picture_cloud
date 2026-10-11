package com.li.lipicturecloud.model.dto.companion;

/** Explicit opt-in; no owner or arbitrary page content is accepted. */
public record PresenceSessionRequest(Boolean enabled, String mode, Integer maxDecisions,
                                     Boolean muted, String intensity) {
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String key, Object value) {
        throw new IllegalArgumentException("Unknown presence request field");
    }
}
