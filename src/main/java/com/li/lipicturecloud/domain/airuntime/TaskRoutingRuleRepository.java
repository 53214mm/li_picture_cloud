package com.li.lipicturecloud.domain.airuntime;

import java.util.List;
import java.util.Optional;

/**
 * 每主体每任务的连接路由规则的持久化端口。(subjectId, task) 唯一。
 */
public interface TaskRoutingRuleRepository {

    Optional<TaskRoutingRule> findBySubjectAndTask(long subjectId, ModelTask task);

    List<TaskRoutingRule> findByOwnerId(long subjectId);

    TaskRoutingRule insert(TaskRoutingRule rule);

    /**
     * 以 revision 为乐观锁写入；after.revision 必须恰好等于 expectedRevision + 1。
     */
    boolean save(TaskRoutingRule after, long expectedRevision);

    /** 仅在 revision 匹配时删除。 */
    boolean delete(long id, long expectedRevision);
}
