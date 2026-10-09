package com.li.lipicturecloud.application.companion.presence;

import java.time.Instant;

public record PresenceSessionView(String sessionId, Instant expiresAt, int remainingDecisions,
                                  String mode) { }
