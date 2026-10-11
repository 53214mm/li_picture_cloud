package com.li.lipicturecloud.application.companion.presence;

import java.time.LocalDate;

/** Separate durable allowance; every reserved attempt stays charged even on failure/cancellation. */
public interface PresenceQuotaGuard {
    int reserve(long subjectId, LocalDate usageDate, int dailyLimit);
    /** Read-only snapshot; no reservation, bucket creation or reset. */
    default Integer used(long subjectId, LocalDate usageDate) { return null; }
}
