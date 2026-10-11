package com.li.lipicturecloud.application.companion.presence;

import java.time.Instant;

public record PresenceDecisionView(String sessionId, String eventId, long sequence,
        String action, String emote, String text, String reason, int remainingDecisions,
        Instant expiresAt, String mode) { }
