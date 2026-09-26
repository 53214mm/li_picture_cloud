package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.companion.view.FeedPictureResult;
import com.li.lipicturecloud.domain.companion.FeedingRun;

import java.util.Objects;

/**
 * reserve 阶段对当前调用者给出的处理决定。
 *
 * <p>它不是数据库中的 {@code FeedingRunStatus}：数据库状态说明 run 目前处于哪里，
 * 本类型则告诉本次请求应该继续分析、回放、等待还是返回拒绝。</p>
 */
public record FeedReservation(Kind kind, FeedingRun run, FeedPictureResult replay) {

    /** 本次 reserve 调用的四种应用层结果。 */
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
