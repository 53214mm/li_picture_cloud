package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.companion.view.FeedPictureResult;
import com.li.lipicturecloud.domain.companion.FeedingRun;

import java.util.Objects;

public record FeedReservation(Kind kind, FeedingRun run, FeedPictureResult replay) {

    public enum Kind { STARTED, REPLAY, IN_PROGRESS, REJECTED }

    public FeedReservation {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(run, "run");
        if ((kind == Kind.REPLAY) != (replay != null)) {
            throw new IllegalArgumentException("only replay reservation carries a result");
        }
    }

    public static FeedReservation started(FeedingRun run) {
        return new FeedReservation(Kind.STARTED, run, null);
    }

    public static FeedReservation replay(FeedingRun run, FeedPictureResult result) {
        return new FeedReservation(Kind.REPLAY, run, Objects.requireNonNull(result, "result"));
    }

    public static FeedReservation inProgress(FeedingRun run) {
        return new FeedReservation(Kind.IN_PROGRESS, run, null);
    }

    public static FeedReservation rejected(FeedingRun run) {
        return new FeedReservation(Kind.REJECTED, run, null);
    }
}
