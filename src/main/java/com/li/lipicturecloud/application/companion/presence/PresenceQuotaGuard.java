package com.li.lipicturecloud.application.companion.presence;

import java.time.LocalDate;

/** Separate durable allowance; every reserved attempt stays charged even on failure/cancellation. */
public interface PresenceQuotaGuard {
    int reserve(long subjectId, LocalDate usageDate, int dailyLimit);
}
