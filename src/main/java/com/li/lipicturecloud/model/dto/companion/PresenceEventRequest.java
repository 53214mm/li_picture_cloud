package com.li.lipicturecloud.model.dto.companion;

import java.time.Instant;

/** Fixed application facts only, never DOM, URLs, form fields or credentials. */
public record PresenceEventRequest(String sessionId, String eventId, Long sequence,
                                   Instant observedAt, String eventType, String page) {
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String key, Object value) {
        throw new IllegalArgumentException("Unknown presence request field");
    }
}
