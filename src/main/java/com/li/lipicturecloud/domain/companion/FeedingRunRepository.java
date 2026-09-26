package com.li.lipicturecloud.domain.companion;

import java.time.Instant;
import java.util.Optional;

/**
 * 可重试喂养运行的持久化端口。
 *
 * <p>所有状态转移都携带 expectedRevision，由实现层转换为 CAS 更新，防止旧尝试覆盖新尝试。</p>
 */
public interface FeedingRunRepository {
    /** 同一个伙伴内按幂等键查找唯一运行。 */
    Optional<FeedingRun> findByKey(long companionId, String idempotencyKey);

    /** 插入一条尚未持久化的 PROCESSING 运行。 */
    FeedingRun insert(FeedingRun run);

    /** 抢占失败或超时运行；只有旧 revision 仍匹配时成功。 */
    boolean restart(long runId, long expectedRevision, Instant now);

    /** 将 PROCESSING 运行完成并关联唯一成长记录。 */
    boolean complete(long runId, long expectedRevision, long growthRecordId, Instant now);

    /** 记录可重试的安全失败。 */
    boolean fail(long runId, long expectedRevision, String safeCode, String safeMessage, Instant now);

    /** 记录不可通过同一请求重试绕过的业务拒绝。 */
    boolean reject(long runId, long expectedRevision, String safeCode, String safeMessage, Instant now);
}
