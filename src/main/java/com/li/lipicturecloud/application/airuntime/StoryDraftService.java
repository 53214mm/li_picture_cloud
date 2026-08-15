package com.li.lipicturecloud.application.airuntime;

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
 * 图片故事草稿应用服务：授权图片 → 语言路由生成大纲 → 用户确认 → 草稿 → 用户确认 → 保存。
 *
 * <p>平台钱包路径走试用账本预占/结算/释放（超限停止不自动扣费）；BYOK 免费且失败不静默回退。
 * 生成文本是伙伴内容，不复制用户原文；日志不记录大纲/草稿正文。原图永不覆盖。</p>
 */
@Service
public class StoryDraftService {

    public static final String CAPABILITY_OUTLINE = "STORY_DRAFT_OUTLINE";
    public static final String CAPABILITY_DRAFT = "STORY_DRAFT_DRAFT";
    public static final String PROMPT_TEMPLATE_VERSION = "story-v1";
    public static final long OUTLINE_TRIAL_COST = 2L;
    public static final long DRAFT_TRIAL_COST = 3L;

    private static final String SYSTEM_PROMPT = "你是图像伙伴。为用户选择的图片写一个温暖的第一人称短篇故事。"
            + "只输出简体中文正文，不输出 markdown、标题、链接或图片里的原文。";
    private static final String OUTLINE_PROMPT_TEMPLATE =
            "用户选了 %d 张图片%s。请先为这个故事写一段 60 字以内的大纲，只说情节走向，不要展开细节。";
    private static final String DRAFT_PROMPT_TEMPLATE =
            "大纲：%s%n请按大纲把故事写成 200 字以内的草稿，语气温暖克制。";

    private final CreationTaskRepository taskRepository;
    private final CreationLineageRepository lineageRepository;
    private final CreationServiceSupport support;
    private final LanguageRouter languageRouter;
    private final LanguageModelInvoker languageInvoker;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final PlatformTrialLedgerService trialLedger;
    private final Clock clock;

    public StoryDraftService(CreationTaskRepository taskRepository,
                             CreationLineageRepository lineageRepository,
                             CreationServiceSupport support,
                             LanguageRouter languageRouter,
                             LanguageModelInvoker languageInvoker,
                             ObjectProvider<ChatModel> chatModelProvider,
                             PlatformTrialLedgerService trialLedger,
                             Clock clock) {
        this.taskRepository = taskRepository;
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
        return taskRepository.insert(CreationTask.create(subject.userId(), CreationKind.STORY_DRAFT,
                ids, idempotencyKey, clock.instant()));
    }

    public CreationTask outline(AuthorizationSubject subject, long taskId) {
        CreationTask task = support.requireOwnedOfKind(subject, taskId, CreationKind.STORY_DRAFT);
        try {
            task = support.transition(task, task.startOutlining(clock.instant()));
        } catch (IllegalStateException wrongState) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务状态已变化，请刷新后重试");
        }
        boolean platform = false;
        try {
            // 执行前重新校验：分享撤销/移动后不得让旧选择越过权限边界（规格 §5）。
            support.reauthorizePictures(subject, task);
            ModelRouteDecision route = languageRouter.decide(subject.userId());
            platform = !route.isByok();
            if (platform) {
                trialLedger.reserve(subject.userId(), OUTLINE_TRIAL_COST);
            }
            String text = invoke(route, OUTLINE_PROMPT_TEMPLATE.formatted(
                    task.sourcePictureIds().size(), support.grounding(task.sourcePictureIds())));
            // 关键：转移成功后把 task 推进到当前状态，后续失败必须基于最新状态写 FAILED。
            task = support.transition(task,
                    task.completeOutline(text, route.isByok() ? route.connection().id() : null,
                            clock.instant()));
            recordLineage(task, CAPABILITY_OUTLINE, support.modelCode(route),
                    support.costSource(route));
            if (platform) {
                trialLedger.settle(subject.userId(), OUTLINE_TRIAL_COST);
            }
            return task;
        } catch (RuntimeException failure) {
            if (platform) {
                support.releaseTrial(trialLedger, subject.userId(), OUTLINE_TRIAL_COST);
            }
            try {
                support.transition(task, task.fail(clock.instant()));
            } catch (RuntimeException alreadyTerminal) {
                // 已终态则无需再写 FAILED。
            }
            throw failure;
        }
    }

    public CreationTask confirmOutline(AuthorizationSubject subject, long taskId) {
        CreationTask task = support.requireOwnedOfKind(subject, taskId, CreationKind.STORY_DRAFT);
        try {
            return support.transition(task, task.confirmOutline(clock.instant()));
        } catch (IllegalStateException wrongState) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务状态已变化，请刷新后重试");
        }
    }

    public CreationTask draft(AuthorizationSubject subject, long taskId) {
        CreationTask task = support.requireOwnedOfKind(subject, taskId, CreationKind.STORY_DRAFT);
        try {
            task = support.transition(task, task.confirmOutline(clock.instant()));
        } catch (IllegalStateException wrongState) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务状态已变化，请刷新后重试");
        }
        boolean platform = false;
        try {
            // 执行前重新校验：分享撤销/移动后不得让旧选择越过权限边界（规格 §5）。
            support.reauthorizePictures(subject, task);
            ModelRouteDecision route = languageRouter.decide(subject.userId());
            platform = !route.isByok();
            if (platform) {
                trialLedger.reserve(subject.userId(), DRAFT_TRIAL_COST);
            }
            String text = invoke(route, DRAFT_PROMPT_TEMPLATE.formatted(task.outlineText()));
            // 关键：转移成功后把 task 推进到当前状态，后续失败必须基于最新状态写 FAILED。
            task = support.transition(task, task.completeDraft(text, clock.instant()));
            recordLineage(task, CAPABILITY_DRAFT, support.modelCode(route),
                    support.costSource(route));
            if (platform) {
                trialLedger.settle(subject.userId(), DRAFT_TRIAL_COST);
            }
            return task;
        } catch (RuntimeException failure) {
            if (platform) {
                support.releaseTrial(trialLedger, subject.userId(), DRAFT_TRIAL_COST);
            }
            try {
                support.transition(task, task.fail(clock.instant()));
            } catch (RuntimeException alreadyTerminal) {
                // 已终态则无需再写 FAILED。
            }
            throw failure;
        }
    }

    public CreationTask save(AuthorizationSubject subject, long taskId) {
        CreationTask task = support.requireOwnedOfKind(subject, taskId, CreationKind.STORY_DRAFT);
        try {
            CreationTask saving = support.transition(task, task.confirmDraft(clock.instant()));
            return support.transition(saving, saving.completeSave(saving.draftText(), clock.instant()));
        } catch (IllegalStateException wrongState) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务状态已变化，请刷新后重试");
        }
    }

    public List<CreationTask> list(AuthorizationSubject subject, int limit) {
        Objects.requireNonNull(subject, "subject");
        return taskRepository.findBySubjectIdAndKind(subject.userId(), CreationKind.STORY_DRAFT,
                limit).stream().map(support::applyExpiry).toList();
    }

    private String invoke(ModelRouteDecision route, String userPrompt) {
        List<ChatTurn> turns = List.of(ChatTurn.system(SYSTEM_PROMPT), ChatTurn.user(userPrompt));
        String text;
        if (route.isByok()) {
            text = languageInvoker.stream(route, turns).collectList().block().stream()
                    .collect(Collectors.joining());
        } else {
            // 平台路径与伙伴对话一致：走 Spring AI DashScope ChatModel。
            ChatModel chatModel = chatModelProvider.getIfAvailable();
            if (chatModel == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "故事生成模型暂不可用");
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
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "故事生成失败：模型返回为空");
        }
        return text;
    }

    private void recordLineage(CreationTask task, String capabilityId, String modelCode,
                               String costSource) {
        for (Long pictureId : task.sourcePictureIds()) {
            lineageRepository.append(new CreationLineage(null, task.id(), pictureId, null,
                    capabilityId, modelCode, PROMPT_TEMPLATE_VERSION, costSource,
                    clock.instant()));
        }
    }
}
