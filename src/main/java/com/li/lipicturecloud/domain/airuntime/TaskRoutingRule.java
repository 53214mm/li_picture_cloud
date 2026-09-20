package com.li.lipicturecloud.domain.airuntime;

import java.util.Objects;

/**
 * 每主体每任务一行的路由规则：connectionId 为空表示使用平台默认连接。
 */
public record TaskRoutingRule(
        Long id,
        long subjectId,
        ModelTask task,
        Long connectionId,
        long revision) {

    public TaskRoutingRule {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (subjectId <= 0 || revision < 0) {
            throw new IllegalArgumentException("invalid routing identity or revision");
        }
        Objects.requireNonNull(task, "task");
        if (connectionId != null && connectionId <= 0) {
            throw new IllegalArgumentException("connectionId must be positive or null");
        }
    }

    public static TaskRoutingRule create(long subjectId, ModelTask task, Long connectionId) {
        return new TaskRoutingRule(null, subjectId, task, connectionId, 0L);
    }

    public static TaskRoutingRule restore(Long id, long subjectId, ModelTask task,
                                          Long connectionId, long revision) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("persisted id must be positive");
        }
        return new TaskRoutingRule(id, subjectId, task, connectionId, revision);
    }

    public TaskRoutingRule withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new TaskRoutingRule(persistedId, subjectId, task, connectionId, revision);
    }

    public TaskRoutingRule routeTo(Long nextConnectionId) {
        return new TaskRoutingRule(id, subjectId, task, nextConnectionId,
                Math.addExact(revision, 1L));
    }
}
