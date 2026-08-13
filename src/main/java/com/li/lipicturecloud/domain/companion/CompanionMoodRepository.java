package com.li.lipicturecloud.domain.companion;

import java.util.Optional;

/**
 * 伙伴当前情绪的持久化端口。
 */
public interface CompanionMoodRepository {

    Optional<CompanionMood> findByCompanionId(long companionId);

    /**
     * 首次创建中性情绪行；唯一键冲突时由实现重读并返回已有行。
     */
    CompanionMood insert(CompanionMood mood);

    /**
     * 以 revision 为乐观锁写入；after.revision 必须恰好等于 expectedRevision + 1。
     */
    boolean save(CompanionMood after, long expectedRevision);
}
