package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CreationCandidate;
import com.li.lipicturecloud.domain.airuntime.CreationCandidateRepository;
import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationStatus;
import com.li.lipicturecloud.domain.airuntime.CreationTask;
import com.li.lipicturecloud.domain.airuntime.CreationTaskRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 表情草稿应用服务。
 *
 * <p><b>玩法当前暂未开放：</b>文字表情候选必须来自"授权图片的视觉理解"（提取人物、
 * 动作、表情或物体元素的安全摘要），不能用图片数量与粗粒度分类模拟图片内容。
 * 在视觉理解执行器（随统一能力内核 discover/invoke/observe 一起设计与实现）落地前，
 * {@code generate} 一律明确失败：不调用语言或视觉模型、不预占/结算额度、不生成候选。
 * 任务创建/候选读取等 API 保留，供能力就绪后恢复闭环。</p>
 */
@Service
public class EmojiDraftService {

    private static final Logger log = LoggerFactory.getLogger(EmojiDraftService.class);

    public static final String NOT_OPEN_YET_MESSAGE =
            "文字表情草稿暂未开放：生成候选需要先对授权图片做视觉理解，提取其中的人物、"
                    + "动作、表情或物体元素。在视觉理解能力落地前不会生成候选，也不会调用模型。";

    /** 报价/血缘标识常量：配方工坊报价与历史血缘仍引用它们，能力恢复后继续使用。 */
    public static final String CAPABILITY_CANDIDATES = "EMOJI_DRAFT_CANDIDATES";
    public static final String PROMPT_TEMPLATE_VERSION = "emoji-v1";
    public static final long GENERATE_TRIAL_COST = 1L;

    private final CreationTaskRepository taskRepository;
    private final CreationCandidateRepository candidateRepository;
    private final CreationServiceSupport support;
    private final Clock clock;

    public EmojiDraftService(CreationTaskRepository taskRepository,
                             CreationCandidateRepository candidateRepository,
                             CreationServiceSupport support,
                             Clock clock) {
        this.taskRepository = taskRepository;
        this.candidateRepository = candidateRepository;
        this.support = support;
        this.clock = clock;
    }

    public CreationTask create(AuthorizationSubject subject, List<Long> pictureIds,
                               String idempotencyKey) {
        Objects.requireNonNull(subject, "subject");
        if (subject.platformAdmin()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "平台管理员不参与创作");
        }
        if (idempotencyKey == null || idempotencyKey.length() != 36) {
            idempotencyKey = UUID.randomUUID().toString();
        }
        List<Long> ids = support.requireValidPictureIds(pictureIds);
        support.reauthorizePictureIds(subject, ids);
        return taskRepository.insert(CreationTask.create(subject.userId(), CreationKind.EMOJI_DRAFT,
                ids, idempotencyKey, clock.instant()));
    }

    public CreationTask generate(AuthorizationSubject subject, long taskId) {
        CreationTask task = support.requireOwnedOfKind(subject, taskId, CreationKind.EMOJI_DRAFT);
        // 玩法暂未开放：没有视觉理解执行器时不得用图片数量/分类模拟"从图片提取元素"。
        // 明确失败并把任务转入安全失败终态；不调用任何模型、不预占/结算额度。
        try {
            task = support.transition(task, task.fail(clock.instant()));
        } catch (IllegalStateException alreadyTerminal) {
            // 已终态则无需再写 FAILED。
        }
        log.warn("emoji_generate_unavailable subjectId={} taskId={} reason=NOT_OPEN_YET",
                subject.userId(), task.id());
        throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, NOT_OPEN_YET_MESSAGE);
    }

    public CreationTask select(AuthorizationSubject subject, long taskId, int index) {
        CreationTask task = support.requireOwnedOfKind(subject, taskId, CreationKind.EMOJI_DRAFT);
        List<CreationCandidate> candidates = candidateRepository.findByTaskId(taskId);
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该任务还没有候选");
        }
        if (index < 0 || index >= candidates.size()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "候选序号超出范围");
        }
        try {
            return support.transition(task, task.selectDraft(candidates.get(index).text(),
                    clock.instant()));
        } catch (IllegalStateException wrongState) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务状态已变化，请刷新后重试");
        }
    }

    public CreationTask save(AuthorizationSubject subject, long taskId) {
        CreationTask task = support.requireOwnedOfKind(subject, taskId, CreationKind.EMOJI_DRAFT);
        try {
            return support.transition(task, task.completeSave(task.draftText(), clock.instant()));
        } catch (IllegalStateException wrongState) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务状态已变化，请刷新后重试");
        }
    }

    public List<CreationTask> list(AuthorizationSubject subject, int limit) {
        Objects.requireNonNull(subject, "subject");
        return taskRepository.findBySubjectIdAndKind(subject.userId(), CreationKind.EMOJI_DRAFT,
                limit).stream().map(support::applyExpiry).toList();
    }

    public List<CreationCandidate> candidates(AuthorizationSubject subject, long taskId) {
        support.requireOwnedOfKind(subject, taskId, CreationKind.EMOJI_DRAFT);
        return candidateRepository.findByTaskId(taskId);
    }
}
