package com.li.lipicturecloud.domain.companion;

import java.time.Instant;
import java.util.Optional;

/**
 * 每个用户最近一次登录的时间。登录之后开始新的一段对话。
 */
public interface ChatSessionBoundaryRepository {

    Optional<Instant> lastLogin(long subjectId);

    void recordLogin(long subjectId, Instant loginTime);

    default long chatEpoch(long subjectId) { return 0; }
    default void advanceChatEpoch(long subjectId) { }
}
