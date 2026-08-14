package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CreationCandidate;
import com.li.lipicturecloud.domain.airuntime.CreationCandidateRepository;
import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationLineage;
import com.li.lipicturecloud.domain.airuntime.CreationLineageRepository;
import com.li.lipicturecloud.domain.airuntime.CreationStatus;
import com.li.lipicturecloud.domain.airuntime.CreationTask;
import com.li.lipicturecloud.domain.airuntime.CreationTaskRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 表情草稿应用服务：授权图片 → 语言路由生成文字版表情候选（不依赖图像模型）
 * → 用户选中其一 → 保存为文本作品。平台走试用账本，BYOK 免费且失败不静默回退。
 */
@Service
public class EmojiDraftService {

    public static final String CAPABILITY_CANDIDATES = "EMOJI_DRAFT_CANDIDATES";
    public static final String PROMPT_TEMPLATE_VERSION = "emoji-v1";
    public static final long GENERATE_TRIAL_COST = 1L;

    private static final String SYSTEM_PROMPT = "你是图像伙伴。为用户选择的图片生成文字版表情候选。"
            + "每条候选 8-40 字，用伙伴的第一人称口吻，温和有趣；不要序号、markdown、链接或图片原文。";
    private static final String GENERATE_PROMPT_TEMPLATE =
            "用户选了 %d 张图片%s。请输出 3 条文字表情候选，每条单独一行。";

    private final CreationTaskRepository taskRepository;
    private final CreationCandidateRepository candidateRepository;
    private final CreationLineageRepository lineageRepository;
    private final CreationServiceSupport support;
    private final LanguageRouter languageRouter;
    private final LanguageModelInvoker languageInvoker;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final PlatformTrialLedgerService trialLedger;
    private final Clock clock;

    public EmojiDraftService(CreationTaskRepository taskRepository,
                             CreationCandidateRepository candidateRepository,
                             CreationLineageRepository lineageRepository,
                             CreationServiceSupport support,
                             LanguageRouter languageRouter,
                             LanguageModelInvoker languageInvoker,
                             ObjectProvider<ChatModel> chatModelProvider,
                             PlatformTrialLedgerService trialLedger,
                             Clock clock) {
        this.taskRepository = taskRepository;
        this.candidateRepository = candidateRepository;
        this.lineageRepository = lineageRepository;
        this.support = support;
        this.languageRouter = languageRouter;
        this.languageInvoker = languageInvoker;
        this.chatModelProvider = chatModelProvider;
        this.trialLedger = trialLedger;
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
        CreationTask task = support.requireOwned(subject, taskId);
        try {
            task = support.transition(task, task.startOutlining(clock.instant()));
        } catch (IllegalStateException wrongState) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务状态已变化，请刷新后重试");
        }
        boolean platform = false;
        try {
            support.reauthorizePictures(subject, task);
            ModelRouteDecision route = languageRouter.decide(subject.userId());
            platform = !route.isByok();
            if (platform) {
                trialLedger.reserve(subject.userId(), GENERATE_TRIAL_COST);
            }
            String text = invoke(route, GENERATE_PROMPT_TEMPLATE.formatted(
                    task.sourcePictureIds().size(), support.grounding(task.sourcePictureIds())));
            List<String> candidates = parseCandidates(text);
            candidateRepository.appendAll(task.id(), candidates, clock.instant());
            // 关键：转移成功后把 task 推进到当前状态，后续失败必须基于最新状态写 FAILED。
            task = support.transition(task,
                    task.completeOutline(null, route.isByok() ? route.connection().id() : null,
                            clock.instant()));
            recordLineage(task, CAPABILITY_CANDIDATES, support.modelCode(route),
                    support.costSource(route));
            if (platform) {
                trialLedger.settle(subject.userId(), GENERATE_TRIAL_COST);
            }
            return task;
        } catch (RuntimeException failure) {
            if (platform) {
                support.releaseTrial(trialLedger, subject.userId(), GENERATE_TRIAL_COST);
            }
            try {
                support.transition(task, task.fail(clock.instant()));
            } catch (RuntimeException alreadyTerminal) {
                // 已终态则无需再写 FAILED。
            }
            throw failure;
        }
    }

    public CreationTask select(AuthorizationSubject subject, long taskId, int index) {
        CreationTask task = support.requireOwned(subject, taskId);
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
        CreationTask task = support.requireOwned(subject, taskId);
        try {
            return support.transition(task, task.completeSave(task.draftText(), clock.instant()));
        } catch (IllegalStateException wrongState) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务状态已变化，请刷新后重试");
        }
    }

    public List<CreationTask> list(AuthorizationSubject subject, int limit) {
        Objects.requireNonNull(subject, "subject");
        return taskRepository.findBySubjectId(subject.userId(), limit).stream()
                .filter(task -> task.kind() == CreationKind.EMOJI_DRAFT)
                .map(support::applyExpiry)
                .toList();
    }

    public List<CreationCandidate> candidates(AuthorizationSubject subject, long taskId) {
        support.requireOwned(subject, taskId);
        return candidateRepository.findByTaskId(taskId);
    }

    private String invoke(ModelRouteDecision route, String userPrompt) {
        List<ChatTurn> turns = List.of(ChatTurn.system(SYSTEM_PROMPT), ChatTurn.user(userPrompt));
        String text;
        if (route.isByok()) {
            text = languageInvoker.stream(route, turns).collectList().block().stream()
                    .collect(Collectors.joining());
        } else {
            ChatModel chatModel = chatModelProvider.getIfAvailable();
            if (chatModel == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "表情生成模型暂不可用");
            }
            java.util.List<org.springframework.ai.chat.messages.Message> messages =
                    new java.util.ArrayList<>();
            for (ChatTurn turn : turns) {
                messages.add(switch (turn.role()) {
                    case ChatTurn.ROLE_SYSTEM ->
                            new org.springframework.ai.chat.messages.SystemMessage(turn.content());
                    case ChatTurn.ROLE_ASSISTANT ->
                            new org.springframework.ai.chat.messages.AssistantMessage(turn.content());
                    default -> new org.springframework.ai.chat.messages.UserMessage(turn.content());
                });
            }
            text = chatModel.call(new Prompt(messages)).getResult().getOutput().getText();
        }
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "表情生成失败：模型返回为空");
        }
        return text;
    }

    /** 逐行解析候选：每条必须是安全纯文本；无有效候选则大声失败。 */
    private List<String> parseCandidates(String raw) {
        List<String> candidates = raw.lines()
                .map(String::strip)
                .map(line -> line.replaceAll("^[-*•\\d.、）\\)\\s]+", ""))
                .filter(line -> !line.isEmpty())
                .filter(line -> line.codePointCount(0, line.length()) <= 200)
                .filter(line -> line.codePoints().noneMatch(Character::isISOControl))
                .filter(line -> !line.contains("http://") && !line.contains("https://"))
                .limit(CreationCandidate.MAX_CANDIDATES)
                .toList();
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "表情生成失败：候选为空");
        }
        return candidates;
    }

    private void recordLineage(CreationTask task, String capabilityId, String modelCode,
                               String costSource) {
        for (Long pictureId : task.sourcePictureIds()) {
            lineageRepository.append(new CreationLineage(null, task.id(), pictureId,
                    capabilityId, modelCode, PROMPT_TEMPLATE_VERSION, costSource,
                    clock.instant()));
        }
    }
}
