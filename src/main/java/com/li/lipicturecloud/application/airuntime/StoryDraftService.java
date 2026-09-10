package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationLineage;
import com.li.lipicturecloud.domain.airuntime.CreationLineageRepository;
import com.li.lipicturecloud.domain.airuntime.CreationStatus;
import com.li.lipicturecloud.domain.airuntime.CreationTask;
import com.li.lipicturecloud.domain.airuntime.CreationTaskRepository;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.ModelUsageSnapshot;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(StoryDraftService.class);

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
    private final ModelUsageService usageService;
    private final Clock clock;

    public StoryDraftService(CreationTaskRepository taskRepository,
                             CreationLineageRepository lineageRepository,
                             CreationServiceSupport support,
                             LanguageRouter languageRouter,
                             LanguageModelInvoker languageInvoker,
                             ObjectProvider<ChatModel> chatModelProvider,
                             PlatformTrialLedgerService trialLedger,
                             ModelUsageService usageService,
                             Clock clock) {
        this.taskRepository = taskRepository;
        this.lineageRepository = lineageRepository;
        this.support = support;
        this.languageRouter = languageRouter;
        this.languageInvoker = languageInvoker;
        this.chatModelProvider = chatModelProvider;
        this.trialLedger = trialLedger;
        this.usageService = usageService;
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
        return generateWithLedger(subject, task, OUTLINE_TRIAL_COST, "outline",
                source -> OUTLINE_PROMPT_TEMPLATE.formatted(source.sourcePictureIds().size(),
                        support.grounding(source.sourcePictureIds())),
                (current, text, route) -> current.completeOutline(text,
                        route.isByok() ? route.connection().id() : null, clock.instant()),
                CAPABILITY_OUTLINE);
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
        return generateWithLedger(subject, task, DRAFT_TRIAL_COST, "draft",
                source -> DRAFT_PROMPT_TEMPLATE.formatted(source.outlineText()),
                (current, text, route) -> current.completeDraft(text, clock.instant()),
                CAPABILITY_DRAFT);
    }

    /** 把生成文本应用到任务的策略（大纲与草稿的差异只在这里）。 */
    @FunctionalInterface
    private interface TextApplier {
        CreationTask apply(CreationTask task, String text, ModelRouteDecision route);
    }

    /**
     * 大纲与草稿共享的"路由 → 预占 → 调用 → 记录 → 结算/释放 → 失败转移"执行模板。
     *
     * <p>账本状态显式区分：{@code reservationHeld} 只在 {@code reserve()} 成功返回后为真
     * （预占失败绝不能释放其他并发请求的预占）；{@code modelInvocationStarted} 之后的失败
     * 才记模型失败用量（预占/授权失败时模型根本没有被调用）；{@code modelReturned} 为真
     * 表示供应商成本已经产生，后续任何后处理失败都只结算并记录业务失败，绝不释放预占。</p>
     */
    private CreationTask generateWithLedger(AuthorizationSubject subject, CreationTask task,
                                            long trialCost, String step,
                                            java.util.function.Function<CreationTask, String> promptFactory,
                                            TextApplier textApplier, String capabilityId) {
        boolean reservationHeld = false;
        boolean modelInvocationStarted = false;
        boolean modelReturned = false;
        ModelRouteDecision route = null;
        try {
            // 执行前重新校验：分享撤销/移动后不得让旧选择越过权限边界（规格 §5）。
            support.reauthorizePictures(subject, task);
            route = languageRouter.decide(subject.userId());
            if (!route.isByok()) {
                trialLedger.reserve(subject.userId(), trialCost);
                // 预占成功才持有：reserve 抛错（余额不足等）时本次请求没有预占，
                // 异常路径不得 release，否则会释放其他并发请求的预占。
                reservationHeld = true;
            }
            modelInvocationStarted = true;
            CreationServiceSupport.LanguageInvocation invocation = invoke(route, promptFactory.apply(task));
            // 模型已成功返回：供应商成本已经产生，随后的任何后处理失败都不再释放预占。
            modelReturned = true;
            // 使用记录紧跟模型调用结果（成功即记，独立于后续任务状态转移）。
            recordUsage(subject.userId(), route, true, invocation.usage(), null);
            if (invocation.text() == null || invocation.text().isBlank()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "故事生成失败：模型返回为空");
            }
            CreationTask updated;
            try {
                updated = support.transition(task, textApplier.apply(task, invocation.text(), route));
            } catch (IllegalArgumentException unsafeText) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "故事生成内容不符合安全文本要求，请重试");
            }
            recordLineage(updated, capabilityId, support.modelCode(route), support.costSource(route));
            if (reservationHeld) {
                trialLedger.settle(subject.userId(), trialCost);
            }
            return updated;
        } catch (RuntimeException failure) {
            // 只有模型调用确实开始过且未成功返回才补记失败用量；预占失败、授权失败等
            // 没有出站的错误不得记成一次"模型调用失败"。
            if (modelInvocationStarted && !modelReturned && route != null) {
                recordUsage(subject.userId(), route, false, ModelUsageSnapshot.none(),
                        CreationServiceSupport.safeErrorCode(failure));
            }
            if (reservationHeld) {
                if (modelReturned) {
                    // 模型已成功返回：即使持久化/校验失败也结算预占，业务失败单独记录，
                    // 绝不把已产生的平台成本退给用户。
                    settleTrialQuietly(subject.userId(), trialCost, step);
                    log.warn("story_" + step + "_post_model_failed subjectId={} taskId={} exceptionType={}",
                            subject.userId(), task.id(), failure.getClass().getName());
                } else {
                    support.releaseTrial(trialLedger, subject.userId(), trialCost);
                }
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

    private CreationServiceSupport.LanguageInvocation invoke(ModelRouteDecision route, String userPrompt) {
        List<ChatTurn> turns = List.of(ChatTurn.system(SYSTEM_PROMPT), ChatTurn.user(userPrompt));
        if (route.isByok()) {
            String text = languageInvoker.stream(route, turns).collectList().block().stream()
                    .collect(Collectors.joining());
            return new CreationServiceSupport.LanguageInvocation(text, ModelUsageSnapshot.none());
        }
        // 平台路径与伙伴对话一致：走 Spring AI DashScope ChatModel；用量取自响应元数据。
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
        org.springframework.ai.chat.model.ChatResponse response =
                chatModel.call(new Prompt(messages));
        String text = response.getResult() == null || response.getResult().getOutput() == null
                ? null : response.getResult().getOutput().getText();
        return new CreationServiceSupport.LanguageInvocation(text,
                CreationServiceSupport.usageOf(response));
    }

    /** 平台语言模型调用成功/失败的追加式使用记录；记录失败只告警不掩盖主流程。 */
    private void recordUsage(long subjectId, ModelRouteDecision route, boolean success,
                             ModelUsageSnapshot usage, String safeErrorCode) {
        try {
            if (success) {
                usageService.recordSuccess(subjectId, ModelTask.LANGUAGE_AGENT,
                        route.isByok() ? route.connection().id() : null,
                        support.providerOf(route), support.modelCode(route),
                        support.costSourceOf(route), usage);
            } else {
                usageService.recordFailure(subjectId, ModelTask.LANGUAGE_AGENT,
                        route.isByok() ? route.connection().id() : null,
                        support.providerOf(route), support.modelCode(route),
                        support.costSourceOf(route), usage, safeErrorCode);
            }
        } catch (RuntimeException recordFailure) {
            log.warn("story_usage_record_failed subjectId={} task={}",
                    subjectId, ModelTask.LANGUAGE_AGENT.name());
        }
    }

    private void settleTrialQuietly(long subjectId, long amount, String step) {
        try {
            trialLedger.settle(subjectId, amount);
        } catch (RuntimeException settleFailure) {
            log.warn("story_trial_settle_failed subjectId={} amount={} step={}",
                    subjectId, amount, step);
        }
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
