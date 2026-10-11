package com.li.lipicturecloud.application.companion.presence;

import com.li.lipicturecloud.application.airuntime.ChatTurn;
import com.li.lipicturecloud.application.airuntime.LanguageStreamDiagnostics;
import java.time.Instant;
import java.util.List;

/** Only returned for the authenticated owner's manual BYOK session; never persisted. */
public record PresenceDebugView(Event event, List<ChatTurn> messages, Parameters parameters,
        String rawBody, boolean rawBodyComplete, boolean rawBodyTruncated, PresenceDecision parsedDecision,
        LanguageStreamDiagnostics.Snapshot stream, List<String> redactedFields) {
    @Override public String toString() { return "PresenceDebugView[manual request-local content omitted]"; }
    public record Event(String scope, String sessionId, String eventId, long sequence, Instant observedAt,
                        String eventType, String page) { }
    public record Parameters(String provider, String model, boolean stream, int maxTokens,
                             int collectionTimeoutSeconds, int maxOutputChars, String thinking, String temperature) { }
}
