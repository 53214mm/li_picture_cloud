package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationLineage;
import com.li.lipicturecloud.domain.airuntime.CreationLineageRepository;
import com.li.lipicturecloud.domain.airuntime.CreationStatus;
import com.li.lipicturecloud.domain.airuntime.CreationTask;
import com.li.lipicturecloud.domain.airuntime.CreationTaskRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
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

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;

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
            "用户选了 %d 张图片。请先为这个故事写一段 60 字以内的大纲，只说情节走向，不要展开细节。";
    private static final String DRAFT_PROMPT_TEMPLATE =
            "大纲：%s%n请按大纲把故事写成 200 字以内的草稿，语气温暖克制。";

    private final CreationTaskRepository taskRepository;
    private final CreationLineageRepository lineageRepository;
    private final SpaceAuthorizationAccessService authorization;
    private final LanguageRouter languageRouter;
    private final LanguageModelInvoker languageInvoker;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final PlatformTrialLedgerService trialLedger;
    private final Clock clock;

    public StoryDraftService(CreationTaskRepository taskRepository,
                             CreationLineageRepository lineageRepository,
                             SpaceAuthorizationAccessService authorization,
                             LanguageRouter languageRouter,
                             LanguageModelInvoker languageInvoker,
                             ObjectProvider<ChatModel> chatModelProvider,
                             PlatformTrialLedgerService trialLedger,
                             Clock clock) {
        this.taskRepository = taskRepository;
        this.lineageRepository = lineageRepository;
        this.authorization = authorization;
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
        List<Long> ids = requireValidPictureIds(pictureIds);
        ids.forEach(pictureId -> authorization.checkForUser(PICTURE_VIEW, pictureId,
                subject.userId()));
        return taskRepository.insert(CreationTask.create(subject.userId(), CreationKind.STORY_DRAFT,
                ids, idempotencyKey, clock.instant()));
    }

    public CreationTask outline(AuthorizationSubject subject, long taskId) {
        CreationTask task = requireOwned(subject, taskId);
        task = transition(task, task.startOutlining(clock.instant()));
        ModelRouteDecision route = languageRouter.decide(subject.userId());
        boolean platform = !route.isByok();
        if (platform) {
            trialLedger.reserve(subject.userId(), OUTLINE_TRIAL_COST);
        }
        try {
            String text = invoke(route, OUTLINE_PROMPT_TEMPLATE.formatted(
                    task.sourcePictureIds().size()));
            CreationTask completed = transition(task,
                    task.completeOutline(text, route.isByok() ? route.connection().id() : null,
                            clock.instant()));
            recordLineage(task, CAPABILITY_OUTLINE, modelCode(route), costSource(route));
            if (platform) {
                trialLedger.settle(subject.userId(), OUTLINE_TRIAL_COST);
            }
            return completed;
        } catch (RuntimeException failure) {
            if (platform) {
                releaseTrial(subject.userId(), OUTLINE_TRIAL_COST);
            }
            transition(task, task.fail(clock.instant()));
            throw failure;
        }
    }

    public CreationTask confirmOutline(AuthorizationSubject subject, long taskId) {
        CreationTask task = requireOwned(subject, taskId);
        return transition(task, task.confirmOutline(clock.instant()));
    }

    public CreationTask draft(AuthorizationSubject subject, long taskId) {
        CreationTask task = requireOwned(subject, taskId);
        task = transition(task, task.confirmOutline(clock.instant()));
        ModelRouteDecision route = languageRouter.decide(subject.userId());
        boolean platform = !route.isByok();
        if (platform) {
            trialLedger.reserve(subject.userId(), DRAFT_TRIAL_COST);
        }
        try {
            String text = invoke(route, DRAFT_PROMPT_TEMPLATE.formatted(task.outlineText()));
            CreationTask completed = transition(task,
                    task.completeDraft(text, clock.instant()));
            recordLineage(task, CAPABILITY_DRAFT, modelCode(route), costSource(route));
            if (platform) {
                trialLedger.settle(subject.userId(), DRAFT_TRIAL_COST);
            }
            return completed;
        } catch (RuntimeException failure) {
            if (platform) {
                releaseTrial(subject.userId(), DRAFT_TRIAL_COST);
            }
            transition(task, task.fail(clock.instant()));
            throw failure;
        }
    }

    public CreationTask save(AuthorizationSubject subject, long taskId) {
        CreationTask task = requireOwned(subject, taskId);
        CreationTask saving = transition(task, task.confirmDraft(clock.instant()));
        return transition(saving, saving.completeSave(saving.draftText(), clock.instant()));
    }

    public List<CreationTask> list(AuthorizationSubject subject, int limit) {
        Objects.requireNonNull(subject, "subject");
        return taskRepository.findBySubjectId(subject.userId(), limit).stream()
                .map(this::expireIfStale)
                .toList();
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
            lineageRepository.append(new CreationLineage(null, task.id(), pictureId,
                    capabilityId, modelCode, PROMPT_TEMPLATE_VERSION, costSource,
                    clock.instant()));
        }
    }

    private CreationTask requireOwned(AuthorizationSubject subject, long taskId) {
        CreationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "创作任务不存在"));
        if (task.subjectId() != subject.userId()) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权操作该创作任务");
        }
        return expireIfStale(task);
    }

    /** 确认等待超时的任务惰性转 EXPIRED（终态）。 */
    private CreationTask expireIfStale(CreationTask task) {
        if (task.status() != CreationStatus.AWAITING_CONFIRM
                || !task.updatedTime().plus(java.time.Duration.ofMinutes(30))
                .isBefore(clock.instant())) {
            return task;
        }
        return transition(task, task.expire(clock.instant()));
    }

    private CreationTask transition(CreationTask current, CreationTask after) {
        if (current.id() == null || !taskRepository.save(after, current.revision())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创作任务发生并发冲突，请重试");
        }
        return after;
    }

    private static List<Long> requireValidPictureIds(List<Long> pictureIds) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择至少一张图片");
        }
        if (pictureIds.size() > CreationTask.MAX_SOURCE_PICTURES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "一次创作最多 " + CreationTask.MAX_SOURCE_PICTURES + " 张图片");
        }
        return List.copyOf(pictureIds);
    }

    private static String modelCode(ModelRouteDecision route) {
        return route.isByok() ? route.connection().modelCode() : "qwen-max";
    }

    private static String costSource(ModelRouteDecision route) {
        return route.isByok() ? CostSource.BYOK.name() : CostSource.PLATFORM.name();
    }

    private void releaseTrial(long subjectId, long amount) {
        try {
            trialLedger.release(subjectId, amount);
        } catch (RuntimeException releaseFailure) {
            // 释放失败不得掩盖生成错误。
        }
    }
}
