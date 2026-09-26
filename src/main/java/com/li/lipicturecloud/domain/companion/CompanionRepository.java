package com.li.lipicturecloud.domain.companion;

import java.util.Optional;

/**
 * 伙伴聚合的持久化端口，由领域层定义、基础设施层实现。
 *
 * <p>调用者只表达“查找、锁定、创建、保存伙伴”，不依赖 MyBatis 或表结构。</p>
 */
public interface CompanionRepository {
    /** 按主人查找伙伴，不获取数据库写锁。 */
    Optional<Companion> findByOwnerId(long ownerId);

    /** 在已有事务内读取并锁定伙伴，供最终成长结算使用。 */
    Optional<Companion> findByOwnerIdForUpdate(long ownerId);

    /** 并发安全地唤醒伙伴；同一主人最终只能得到一个伙伴。 */
    Companion createIfAbsent(long ownerId, CompanionBalance balance);

    /**
     * 以 expectedRevision 执行 CAS 保存。
     *
     * @return 仅当数据库仍是预期旧版本且更新成功时返回 true
     */
    boolean save(Companion companionAfter, long expectedRevision);
}
