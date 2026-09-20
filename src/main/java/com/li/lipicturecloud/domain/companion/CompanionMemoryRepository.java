package com.li.lipicturecloud.domain.companion;

import java.util.List;
import java.util.Optional;

/**
 * 来源化记忆的持久化端口。记忆只追加创建、按 revision CAS 更新，不提供物理删除。
 */
public interface CompanionMemoryRepository {

    CompanionMemory append(CompanionMemory memory);

    Optional<CompanionMemory> findById(long id);

    /** 最近记忆（全部状态），按创建时间倒序。 */
    List<CompanionMemory> findRecent(long companionId, int limit);

    /** 仍处于 PENDING/CONFIRMED/DISMISSED 的记忆，用于撤权失效传播。 */
    List<CompanionMemory> findActive(long companionId, int limit);

    /**
     * 以 revision 为乐观锁更新；after.revision 必须恰好等于 expectedRevision + 1。
     */
    boolean save(CompanionMemory after, long expectedRevision);
}
