package com.li.lipicturecloud.domain.companion;

import java.util.Optional;

/**
 * 伙伴与主体之间关系状态的持久化端口。
 */
public interface CompanionRelationshipRepository {

    Optional<CompanionRelationship> findByCompanionAndSubject(long companionId, long subjectId);

    /**
     * 没有关系行时创建中性行；并发创建由唯一键仲裁，输的一方重读赢家行。
     */
    CompanionRelationship createIfAbsent(long companionId, long subjectId);

    /**
     * 以 revision 为乐观锁写入；after.revision 必须恰好等于 expectedRevision + 1。
     */
    boolean save(CompanionRelationship after, long expectedRevision);
}
