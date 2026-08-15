package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationStatus;
import com.li.lipicturecloud.domain.airuntime.CreationTask;
import com.li.lipicturecloud.domain.airuntime.CreationTaskRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;

/**
 * 创作任务服务共享支撑：归属校验、执行前授权复核、CAS 转移、30 分钟确认超时惰性过期、
 * 分类落地线索与安全纯文本校验。日志不记录任何生成文本。
 */
@Component
public class CreationServiceSupport {

    static final java.util.regex.Pattern SAFE_CATEGORY =
            java.util.regex.Pattern.compile("[\\p{L}\\p{N} _\\-]{1,16}");
    private static final Duration CONFIRM_TIMEOUT = Duration.ofMinutes(30);

    private final CreationTaskRepository taskRepository;
    private final SpaceAuthorizationAccessService authorization;
    private final com.li.lipicturecloud.repository.PictureRepository pictureRepository;
    private final Clock clock;

    public CreationServiceSupport(CreationTaskRepository taskRepository,
                                  SpaceAuthorizationAccessService authorization,
                                  com.li.lipicturecloud.repository.PictureRepository pictureRepository,
                                  Clock clock) {
        this.taskRepository = taskRepository;
        this.authorization = authorization;
        this.pictureRepository = pictureRepository;
        this.clock = clock;
    }

    public CreationTask requireOwned(AuthorizationSubject subject, long taskId) {
        CreationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "创作任务不存在"));
        if (task.subjectId() != subject.userId()) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权操作该创作任务");
        }
        return expireIfStale(task);
    }

    /** 归属校验 + 玩法种类守门：跨玩法操作一律按不存在处理，防止状态机被串用。 */
    public CreationTask requireOwnedOfKind(AuthorizationSubject subject, long taskId,
                                           CreationKind kind) {
        CreationTask task = requireOwned(subject, taskId);
        if (task.kind() != kind) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "创作任务不存在");
        }
        return task;
    }

    public CreationTask transition(CreationTask current, CreationTask after) {
        if (current.id() == null || !taskRepository.save(after, current.revision())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创作任务发生并发冲突，请重试");
        }
        return after;
    }

    /** 执行前重新校验：分享撤销/移动后不得让旧选择越过权限边界（规格 §5）。 */
    public void reauthorizePictures(AuthorizationSubject subject, CreationTask task) {
        reauthorizePictureIds(subject, task.sourcePictureIds());
    }

    public void reauthorizePictureIds(AuthorizationSubject subject, List<Long> pictureIds) {
        for (Long pictureId : pictureIds) {
            authorization.checkForUser(PICTURE_VIEW, pictureId, subject.userId());
        }
    }

    /** 从图片的粗粒度分类构建安全的落地线索；不携带图片名、标签或任何用户原文。 */
    public String grounding(List<Long> pictureIds) {
        String categories = pictureIds.stream()
                .map(pictureRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(com.li.lipicturecloud.model.entity.Picture::getCategory)
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(category -> SAFE_CATEGORY.matcher(category).matches())
                .distinct()
                .limit(5)
                .collect(Collectors.joining("、"));
        return categories.isEmpty() ? "" : "（图片分类：" + categories + "）";
    }

    public List<Long> requireValidPictureIds(List<Long> pictureIds) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择至少一张图片");
        }
        if (pictureIds.size() > CreationTask.MAX_SOURCE_PICTURES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "一次创作最多 " + CreationTask.MAX_SOURCE_PICTURES + " 张图片");
        }
        return List.copyOf(pictureIds);
    }

    public void releaseTrial(PlatformTrialLedgerService trialLedger, long subjectId, long amount) {
        try {
            trialLedger.release(subjectId, amount);
        } catch (RuntimeException releaseFailure) {
            // 释放失败不得掩盖生成错误；只记安全字段，预占保留待后续人工处理。
            org.slf4j.LoggerFactory.getLogger(CreationServiceSupport.class)
                    .warn("creation_trial_release_failed subjectId={} amount={}", subjectId, amount);
        }
    }

    /** 惰性应用确认超时过期；列表路径用，避免逐任务回查。并发冲突时返回原任务，绝不拖垮整个列表。 */
    public CreationTask applyExpiry(CreationTask task) {
        try {
            return expireIfStale(task);
        } catch (RuntimeException conflict) {
            return task;
        }
    }

    public static String modelCode(ModelRouteDecision route) {
        return route.isByok() ? route.connection().modelCode() : "qwen-max";
    }

    public static String costSource(ModelRouteDecision route) {
        return route.isByok() ? CostSource.BYOK.name() : CostSource.PLATFORM.name();
    }

    /** 确认等待超时的任务惰性转 EXPIRED（终态）。 */
    private CreationTask expireIfStale(CreationTask task) {
        if (task.status() != CreationStatus.AWAITING_CONFIRM
                || !task.updatedTime().plus(CONFIRM_TIMEOUT).isBefore(clock.instant())) {
            return task;
        }
        return transition(task, task.expire(clock.instant()));
    }
}
