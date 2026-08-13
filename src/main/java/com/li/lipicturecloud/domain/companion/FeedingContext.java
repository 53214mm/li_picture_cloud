package com.li.lipicturecloud.domain.companion;

public record FeedingContext(
        boolean picturePreviouslyFed,
        long lifeExperienceEarnedToday,
        long revisitExperienceEarnedForPicture) {

    public FeedingContext {
        if (lifeExperienceEarnedToday < 0 || revisitExperienceEarnedForPicture < 0) {
            throw new IllegalArgumentException("feeding totals must be nonnegative");
        }
    }
}
