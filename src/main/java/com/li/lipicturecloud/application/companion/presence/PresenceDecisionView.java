package com.li.lipicturecloud.application.companion.presence;

import java.time.Instant;

public record PresenceDecisionView(String sessionId, String eventId, long sequence,
        String action, String emote, String text, String reason, int remainingDecisions,
        Instant expiresAt, String mode, String stage, boolean modelDispatched, boolean attemptConsumed,
        long elapsedMs, int outputChars, Instant nextAllowedAt, Integer dailyRemaining, int dailyLimit, PresenceDebugView debug) {
    public PresenceDecisionView(String sessionId, String eventId, long sequence, String action, String emote, String text,
            String reason, int remainingDecisions, Instant expiresAt, String mode, String stage, boolean modelDispatched,
            boolean attemptConsumed, long elapsedMs, int outputChars, Instant nextAllowedAt, Integer dailyRemaining, int dailyLimit) {
        this(sessionId, eventId, sequence, action, emote, text, reason, remainingDecisions, expiresAt, mode, stage,
                modelDispatched, attemptConsumed, elapsedMs, outputChars, nextAllowedAt, dailyRemaining, dailyLimit, null);
    }
    public PresenceDecisionView(String sessionId, String eventId, long sequence, String action, String emote,
            String text, String reason, int remainingDecisions, Instant expiresAt, String mode) {
        this(sessionId, eventId, sequence, action, emote, text, reason, remainingDecisions, expiresAt, mode,
                "GATE", false, false, 0, 0, null, null, 24, null);
    }
}
