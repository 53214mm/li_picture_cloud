package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.ModelConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRule;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRuleRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 任务路由规则应用服务：按任务槽位维护每主体的连接绑定。
 * connectionId 为空表示显式选择平台；绑定前校验连接归属。
 */
@Service
public class ModelRoutingService {

    private final TaskRoutingRuleRepository routingRepository;
    private final ModelConnectionRepository connectionRepository;

    public ModelRoutingService(TaskRoutingRuleRepository routingRepository,
                               ModelConnectionRepository connectionRepository) {
        this.routingRepository = routingRepository;
        this.connectionRepository = connectionRepository;
    }

    public TaskRoutingRule upsert(long subjectId, ModelTask task, Long connectionId) {
        checkIdentity(subjectId);
        Objects.requireNonNull(task, "task");
        if (task == ModelTask.CONNECTIVITY_CHECK) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "探测任务不支持路由规则");
        }
        if (connectionId != null) {
            connectionRepository.findById(connectionId)
                    .filter(connection -> connection.subjectId() == subjectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARAMS_ERROR,
                            "连接不存在或不属于当前用户"));
        }
        return routingRepository.findBySubjectAndTask(subjectId, task)
                .map(existing -> saveOrConflict(existing, connectionId))
                .orElseGet(() -> {
                    TaskRoutingRule inserted = routingRepository.insert(
                            TaskRoutingRule.create(subjectId, task, connectionId));
                    if (Objects.equals(inserted.connectionId(), connectionId)) {
                        return inserted;
                    }
                    // 并发首写输给了另一写入者：仓储读回的是赢家行，不代表本次请求意图，
                    // 基于赢家行再走一次 CAS 覆盖，绝不静默丢弃本次路由目标。
                    return saveOrConflict(inserted, connectionId);
                });
    }

    private TaskRoutingRule saveOrConflict(TaskRoutingRule existing, Long connectionId) {
        TaskRoutingRule after = existing.routeTo(connectionId);
        if (!routingRepository.save(after, existing.revision())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "路由规则发生并发冲突，请重试");
        }
        return after;
    }

    public List<TaskRoutingRule> list(long subjectId) {
        checkIdentity(subjectId);
        return routingRepository.findByOwnerId(subjectId);
    }

    public boolean delete(long subjectId, ModelTask task) {
        checkIdentity(subjectId);
        Objects.requireNonNull(task, "task");
        return routingRepository.findBySubjectAndTask(subjectId, task)
                .map(existing -> {
                    if (!routingRepository.delete(existing.id(), existing.revision())) {
                        throw new BusinessException(ErrorCode.OPERATION_ERROR,
                                "路由规则发生并发冲突，请重试");
                    }
                    return true;
                })
                .orElse(false);
    }

    private static void checkIdentity(long subjectId) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
    }
}
