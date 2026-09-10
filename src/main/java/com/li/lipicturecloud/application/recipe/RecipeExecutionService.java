package com.li.lipicturecloud.application.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.EmojiDraftService;
import com.li.lipicturecloud.application.airuntime.FusionImageService;
import com.li.lipicturecloud.application.airuntime.LocalCapabilityCatalog;
import com.li.lipicturecloud.application.airuntime.ModelInvocationException;
import com.li.lipicturecloud.application.airuntime.StoryDraftService;
import com.li.lipicturecloud.application.companion.CompanionOpportunityListener;
import com.li.lipicturecloud.application.recipe.view.RecipeExecutionView;
import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationTask;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.recipe.Recipe;
import com.li.lipicturecloud.domain.recipe.RecipeDefinition;
import com.li.lipicturecloud.domain.recipe.RecipeExecution;
import com.li.lipicturecloud.domain.recipe.RecipeExecutionRepository;
import com.li.lipicturecloud.domain.recipe.RecipeExecutionStatus;
import com.li.lipicturecloud.domain.recipe.RecipeIfCondition;
import com.li.lipicturecloud.domain.recipe.RecipeRepository;
import com.li.lipicturecloud.domain.recipe.RecipeStatus;
import com.li.lipicturecloud.domain.recipe.RecipeVersion;
import com.li.lipicturecloud.domain.recipe.RecipeVersionRepository;
import com.li.lipicturecloud.domain.recipe.RecipeWhenType;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.model.entity.Space;
import com.li.lipicturecloud.repository.PictureRepository;
import com.li.lipicturecloud.service.SpaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;

/**
 * 配方执行引擎：试运行（评估 WHEN/IF + 报价，只落 DRY_RUN 记录）、机会触发的待确认记录
 * （阶段 5 的 WHEN 真实触发）与确认执行（重新按记录版本校验 → 授权 → IF 求值 →
 * 调用白名单能力创建创作任务 → 终态转移）。
 *
 * <p>硬边界：</p>
 * <ul>
 *     <li>顺序固定为"校验入参 → 重新授权 → 才读取图片分类/空间"，图片撤权时只记
 *         {@code PICTURE_UNAVAILABLE} 且不携带任何基于未授权图片算出的条件结果；</li>
 *     <li>未开放能力（文字表情草稿、真实多图融合）在发布、试运行、机会触发与执行四处
 *         统一被 {@link LocalCapabilityCatalog} 拒绝——绝不创建注定无法完成的任务，
 *         也不把这种执行标记为 EXECUTED；</li>
 *     <li>来源图片集合在试运行/机会触发时快照落库，确认执行只认这份快照，
 *         用户改选图片必须重新试运行；</li>
 *     <li>DISABLED 配方不产生新执行；条件不满足 → REJECTED；报价是平台试用额度上限承诺，
 *         实际结算仍由各创作服务的试用账本硬上限守护。</li>
 * </ul>
 */
@Service
public class RecipeExecutionService implements CompanionOpportunityListener {

    private static final Logger log = LoggerFactory.getLogger(RecipeExecutionService.class);

    public static final String CONDITION_UNMATCHED = "CONDITION_UNMATCHED";
    public static final String PICTURE_UNAVAILABLE = "PICTURE_UNAVAILABLE";
    public static final String PICTURE_SET_MISSING = "PICTURE_SET_MISSING";
    public static final String PICTURE_SET_CHANGED =
            "所选图片与试运行（或机会触发）时不一致，请重新试运行后再确认执行";

    private static final int PRIVATE_SPACE_TYPE = 0;
    private static final int MAX_CANDIDATE_PICTURES = RecipeExecution.MAX_SOURCE_PICTURES;
    private static final int DEDUPE_SCAN_LIMIT = 20;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository versionRepository;
    private final RecipeExecutionRepository executionRepository;
    private final RecipeDefinitionCodec codec;
    private final ObjectMapper objectMapper;
    private final LocalCapabilityCatalog capabilityCatalog;
    private final StoryDraftService storyDraftService;
    private final EmojiDraftService emojiDraftService;
    private final FusionImageService fusionImageService;
    private final SpaceAuthorizationAccessService authorization;
    private final PictureRepository pictureRepository;
    private final SpaceService spaceService;
    private final GrowthRecordRepository growthRepository;
    private final Clock clock;

    public RecipeExecutionService(RecipeRepository recipeRepository,
                                  RecipeVersionRepository versionRepository,
                                  RecipeExecutionRepository executionRepository,
                                  RecipeDefinitionCodec codec,
                                  ObjectMapper objectMapper,
                                  LocalCapabilityCatalog capabilityCatalog,
                                  StoryDraftService storyDraftService,
                                  EmojiDraftService emojiDraftService,
                                  FusionImageService fusionImageService,
                                  SpaceAuthorizationAccessService authorization,
                                  PictureRepository pictureRepository,
                                  SpaceService spaceService,
                                  GrowthRecordRepository growthRepository,
                                  Clock clock) {
        this.recipeRepository = recipeRepository;
        this.versionRepository = versionRepository;
        this.executionRepository = executionRepository;
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.capabilityCatalog = capabilityCatalog;
        this.storyDraftService = storyDraftService;
        this.emojiDraftService = emojiDraftService;
        this.fusionImageService = fusionImageService;
        this.authorization = authorization;
        this.pictureRepository = pictureRepository;
        this.spaceService = spaceService;
        this.growthRepository = growthRepository;
        this.clock = clock;
    }

    /** 用户手动试运行：授权 → 求值 → 报价，来源图片集合落快照，供确认执行时绑定。 */
    public RecipeExecution dryRun(AuthorizationSubject subject, long recipeId,
                                  List<Long> pictureIds) {
        Recipe recipe = requireOwned(subject, recipeId);
        requireNotDisabled(recipe);
        RecipeVersion version = requireLatestVersion(recipe.id());
        RecipeDefinition definition = codec.decode(version.whenJson(), version.ifJson(),
                version.thenJson());
        requireCapabilityOpen(definition, "dry_run");
        List<Long> ids = requireValidPictureIds(definition.then().capability(), pictureIds);
        // 先重新授权，授权通过后才读取图片分类/空间信息（fail-closed）。
        reauthorizePictures(subject, ids);
        Instant now = clock.instant();
        Evaluation evaluation = evaluate(definition, ids, subject.userId());
        return executionRepository.insert(RecipeExecution.dryRun(recipe.id(), version.version(),
                subject.userId(), now, matchedJson(definition, evaluation),
                quoteJson(definition.then().capability()), RecipeExecution.snapshotJson(ids), now));
    }

    /**
     * 机会触发端口实现（阶段 3 → 阶段 5）：机会类型名与 {@link RecipeWhenType} 一一对应，
     * 未知类型只记录并忽略（机会源扩展时不会打断配方链路）。
     */
    @Override
    public void onOpportunity(long subjectId, long companionId, String whenType, Long pictureId,
                              Instant now) {
        RecipeWhenType when;
        try {
            when = RecipeWhenType.valueOf(whenType);
        } catch (IllegalArgumentException | NullPointerException unknownType) {
            log.info("recipe_opportunity_skipped subjectId={} when={} reason=UNKNOWN_WHEN_TYPE",
                    subjectId, whenType);
            return;
        }
        proposeFromOpportunity(subjectId, companionId, when, pictureId, now);
    }

    /**
     * 机会触发（阶段 5 的 WHEN 闭环）：阶段 3 机会源观察到真实机会、契约/频率/安静时段
     * 守门通过后调用，为 WHEN 匹配的 ENABLED 配方生成"待确认"执行记录。
     *
     * <p>只求值与报价，不调用任何能力；用户确认后才创建创作任务。候选图片来自机会本身
     * （相似图片机会）或伙伴最近完整喂养过的图片（每周回顾/纪念日），逐张重新授权，
     * 未授权图片直接丢弃。</p>
     *
     * @return 本次新建的待确认执行记录（可能为空）
     */
    public List<RecipeExecution> proposeFromOpportunity(long subjectId, long companionId,
                                                        RecipeWhenType when, Long pictureId,
                                                        Instant now) {
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(now, "now");
        List<Long> candidates = candidatePictures(subjectId, companionId, when, pictureId);
        if (candidates.isEmpty()) {
            log.info("recipe_opportunity_skipped subjectId={} when={} reason=NO_AUTHORIZED_PICTURE",
                    subjectId, when.name());
            return List.of();
        }
        List<RecipeExecution> proposed = new ArrayList<>();
        for (Recipe recipe : recipeRepository.findEnabledBySubjectId(subjectId)) {
            RecipeExecution execution = proposeForRecipe(recipe, when, candidates, now);
            if (execution != null) {
                proposed.add(execution);
            }
        }
        return List.copyOf(proposed);
    }

    public RecipeExecution execute(AuthorizationSubject subject, long recipeId, long executionId,
                                   List<Long> pictureIds) {
        Recipe recipe = requireOwned(subject, recipeId);
        if (recipe.status() != RecipeStatus.ENABLED) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "配方未启用，不能执行");
        }
        RecipeExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "执行记录不存在"));
        if (execution.recipeId() != recipeId || execution.subjectId() != subject.userId()
                || !execution.isAwaitingConfirm()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "执行记录状态不可用");
        }
        RecipeVersion version = versionRepository.findByRecipeId(recipe.id()).stream()
                .filter(candidate -> candidate.version() == execution.recipeVersion())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR, "配方版本已失效"));
        RecipeDefinition definition = codec.decode(version.whenJson(), version.ifJson(),
                version.thenJson());
        requireCapabilityOpen(definition, "execute");
        String quoteJson = quoteJson(definition.then().capability());
        List<Long> ids = confirmablePictureIds(execution, pictureIds);
        if (ids.isEmpty()) {
            // 历史记录没有来源图片快照：不猜图片，直接拒绝，避免留下无法确认的记录。
            return reject(execution, PICTURE_SET_MISSING, execution.matchedJson(), quoteJson);
        }
        try {
            // 执行时按当前权限重新校验（收紧不扩大），且必须在读取分类/空间之前完成。
            reauthorizePictures(subject, ids);
        } catch (BusinessException unavailable) {
            // 图片撤权/不可用：只记安全错误码，不携带任何基于未授权图片算出的条件结果。
            log.info("recipe_execution_rejected subjectId={} recipeId={} executionId={} "
                            + "reason={}", subject.userId(), recipeId, executionId, PICTURE_UNAVAILABLE);
            return reject(execution, PICTURE_UNAVAILABLE, unauthorizedSnapshot(definition), quoteJson);
        }
        // 授权通过后才读取图片分类/空间并求值；快照记录的是执行时结果。
        Evaluation evaluation = evaluate(definition, ids, subject.userId());
        String matchedJson = matchedJson(definition, evaluation);
        if (!evaluation.matched()) {
            return reject(execution, CONDITION_UNMATCHED, matchedJson, quoteJson);
        }
        try {
            long taskId = invokeThen(definition.then().capability(), subject, ids, execution.id());
            return complete(execution, taskId, matchedJson, quoteJson);
        } catch (RuntimeException failure) {
            try {
                fail(execution, safeErrorCode(failure), matchedJson, quoteJson);
            } catch (RuntimeException recordFailure) {
                // 记录失败不掩盖原始错误。
            }
            throw failure;
        }
    }

    public List<RecipeExecution> recentByRecipe(AuthorizationSubject subject, long recipeId,
                                                int limit) {
        requireOwned(subject, recipeId);
        return executionRepository.findRecentByRecipeId(recipeId, limit);
    }

    public List<RecipeExecution> recentBySubject(AuthorizationSubject subject, int limit) {
        Objects.requireNonNull(subject, "subject");
        return executionRepository.findRecentBySubjectId(subject.userId(), limit);
    }

    public List<RecipeExecutionView> toViews(List<RecipeExecution> executions) {
        return executions.stream().map(this::toView).toList();
    }

    public RecipeExecutionView toView(RecipeExecution execution) {
        return new RecipeExecutionView(execution.id(), execution.recipeId(),
                execution.recipeVersion(), execution.status().name(), execution.triggeredTime(),
                execution.matchedJson(), execution.quoteJson(), execution.sourcePictureIds(),
                execution.opportunityKey(), execution.creationTaskId(),
                execution.safeErrorCode(), execution.createdTime());
    }

    // ===== 机会触发 =====

    private RecipeExecution proposeForRecipe(Recipe recipe, RecipeWhenType when,
                                             List<Long> candidates, Instant now) {
        RecipeVersion version = versionRepository.findLatest(recipe.id()).orElse(null);
        if (version == null) {
            return null;
        }
        RecipeDefinition definition = codec.decode(version.whenJson(), version.ifJson(),
                version.thenJson());
        if (definition.when().type() != when) {
            return null;
        }
        if (!capabilityCatalog.isOpen(definition.then().capability())) {
            log.info("recipe_opportunity_skipped subjectId={} recipeId={} when={} "
                            + "reason=CAPABILITY_NOT_OPEN capability={}",
                    recipe.subjectId(), recipe.id(), when.name(),
                    definition.then().capability().name());
            return null;
        }
        String opportunityKey = opportunityKey(when, candidates, now);
        if (alreadyProposed(recipe.id(), opportunityKey)) {
            log.info("recipe_opportunity_skipped subjectId={} recipeId={} when={} reason=ALREADY_PENDING",
                    recipe.subjectId(), recipe.id(), when.name());
            return null;
        }
        Evaluation evaluation = evaluate(definition, candidates, recipe.subjectId());
        if (!evaluation.matched()) {
            log.info("recipe_opportunity_skipped subjectId={} recipeId={} when={} "
                            + "reason={}", recipe.subjectId(), recipe.id(), when.name(),
                    CONDITION_UNMATCHED);
            return null;
        }
        RecipeExecution saved = executionRepository.insert(RecipeExecution.pending(recipe.id(),
                version.version(), recipe.subjectId(), now, matchedJson(definition, evaluation),
                quoteJson(definition.then().capability()), RecipeExecution.snapshotJson(candidates),
                opportunityKey, now));
        log.info("recipe_opportunity_proposed subjectId={} recipeId={} executionId={} when={} "
                        + "pictures={}", recipe.subjectId(), recipe.id(), saved.id(), when.name(),
                candidates.size());
        return saved;
    }

    private boolean alreadyProposed(long recipeId, String opportunityKey) {
        for (RecipeExecution recent : executionRepository.findRecentByRecipeId(recipeId,
                DEDUPE_SCAN_LIMIT)) {
            if (opportunityKey.equals(recent.opportunityKey())) {
                return true;
            }
        }
        return executionRepository.findAwaitingConfirm(recipeId).isPresent();
    }

    private static String opportunityKey(RecipeWhenType when, List<Long> candidates, Instant now) {
        return switch (when) {
            case WEEKLY_REVIEW -> {
                java.time.LocalDate today = now.atZone(SHANGHAI).toLocalDate();
                WeekFields fields = WeekFields.ISO;
                yield "WEEKLY_REVIEW-" + today.get(fields.weekBasedYear()) + "-W"
                        + today.get(fields.weekOfWeekBasedYear());
            }
            case ANNIVERSARY -> "ANNIVERSARY-" + now.atZone(SHANGHAI).toLocalDate();
            case SIMILAR_STORY -> "SIMILAR_STORY-" + candidates.get(0);
        };
    }

    /** 候选图片：机会自带图片优先，否则用伙伴最近完整喂养过的图片；逐张重新授权。 */
    private List<Long> candidatePictures(long subjectId, long companionId, RecipeWhenType when,
                                         Long pictureId) {
        List<Long> candidates = pictureId != null
                ? List.of(pictureId)
                : growthRepository.findRecentFedPictureIds(companionId, MAX_CANDIDATE_PICTURES);
        LinkedHashSet<Long> authorized = new LinkedHashSet<>();
        for (Long candidate : candidates) {
            if (candidate == null || candidate <= 0 || authorized.size() >= MAX_CANDIDATE_PICTURES) {
                continue;
            }
            try {
                authorization.checkForUser(PICTURE_VIEW, candidate, subjectId);
                authorized.add(candidate);
            } catch (BusinessException denied) {
                log.info("recipe_opportunity_picture_denied subjectId={} when={} reason={}",
                        subjectId, when.name(), PICTURE_UNAVAILABLE);
            }
        }
        return List.copyOf(authorized);
    }

    /** 确认执行使用的图片集合：只认记录里的快照；客户端传了不同集合就必须重新试运行。 */
    private List<Long> confirmablePictureIds(RecipeExecution execution, List<Long> requested) {
        List<Long> snapshot = execution.sourcePictureIds();
        if (requested == null || requested.isEmpty()) {
            return snapshot;
        }
        List<Long> requestedIds = requested.stream().filter(Objects::nonNull).distinct().toList();
        if (!new LinkedHashSet<>(snapshot).equals(new LinkedHashSet<>(requestedIds))) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, PICTURE_SET_CHANGED);
        }
        return snapshot;
    }

    // ===== 内部求值与执行 =====

    private void requireCapabilityOpen(RecipeDefinition definition, String stage) {
        if (!capabilityCatalog.isOpen(definition.then().capability())) {
            log.info("recipe_capability_gated stage={} capability={} reason=NOT_OPEN",
                    stage, definition.then().capability().name());
            capabilityCatalog.requireOpen(definition.then().capability());
        }
    }

    private long invokeThen(CreationKind capability, AuthorizationSubject subject,
                            List<Long> pictureIds, long executionId) {
        // 执行记录 ID 是幂等单元：重试同一记录沿用同一确定性键，创作服务按唯一键去重，
        // 避免「任务已创建但执行记录转移冲突」的重试产生第二个任务。
        String idempotencyKey = java.util.UUID.nameUUIDFromBytes(
                        ("recipe-execution-" + executionId).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
        CreationTask task = switch (capability) {
            case STORY_DRAFT -> storyDraftService.create(subject, pictureIds, idempotencyKey);
            case EMOJI_DRAFT -> emojiDraftService.create(subject, pictureIds, idempotencyKey);
            case IMAGE_FUSION -> fusionImageService.create(subject, pictureIds, idempotencyKey);
        };
        return Objects.requireNonNull(task.id(), "creation task id");
    }

    private Evaluation evaluate(RecipeDefinition definition, List<Long> pictureIds,
                                long subjectId) {
        long quotedUnits = quotedUnits(definition.then().capability());
        List<Map<String, Object>> results = new ArrayList<>();
        boolean matched = true;
        for (RecipeIfCondition condition : definition.conditions()) {
            boolean conditionMatched = evaluateCondition(condition, pictureIds, subjectId,
                    quotedUnits);
            results.add(Map.of("type", conditionType(condition), "matched", conditionMatched));
            matched &= conditionMatched;
        }
        return new Evaluation(matched, results);
    }

    private boolean evaluateCondition(RecipeIfCondition condition, List<Long> pictureIds,
                                      long subjectId, long quotedUnits) {
        if (condition instanceof RecipeIfCondition.SourceSpacePrivate) {
            for (Long pictureId : pictureIds) {
                Space space = spaceOf(pictureId);
                // 收紧语义：空间缺失/非本人私有一律不满足（fail-closed）。
                if (space == null || space.getSpaceType() == null
                        || space.getSpaceType() != PRIVATE_SPACE_TYPE
                        || space.getUserId() == null || space.getUserId() != subjectId) {
                    return false;
                }
            }
            return true;
        }
        if (condition instanceof RecipeIfCondition.SourceCategory category) {
            for (Long pictureId : pictureIds) {
                String pictureCategory = pictureRepository.findById(pictureId)
                        .map(com.li.lipicturecloud.model.entity.Picture::getCategory)
                        .map(String::strip)
                        .orElse(null);
                if (pictureCategory == null || !pictureCategory.equals(category.category())) {
                    return false;
                }
            }
            return true;
        }
        if (condition instanceof RecipeIfCondition.MaxTrialCost cost) {
            return quotedUnits <= cost.units();
        }
        throw new IllegalStateException("unknown recipe condition kind");
    }

    private Space spaceOf(long pictureId) {
        Long spaceId = pictureRepository.findById(pictureId)
                .map(com.li.lipicturecloud.model.entity.Picture::getSpaceId)
                .orElse(null);
        if (spaceId == null) {
            return null;
        }
        return spaceService.getById(spaceId);
    }

    private List<Long> requireValidPictureIds(CreationKind capability, List<Long> pictureIds) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择执行配方使用的图片");
        }
        if (pictureIds.size() > MAX_CANDIDATE_PICTURES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "一次执行最多 "
                    + MAX_CANDIDATE_PICTURES + " 张图片");
        }
        if (capability == CreationKind.IMAGE_FUSION && pictureIds.size() < 2) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "多图融合至少需要 2 张图片");
        }
        for (Long pictureId : pictureIds) {
            if (pictureId == null || pictureId <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 ID 不合法");
            }
        }
        return List.copyOf(pictureIds);
    }

    private void reauthorizePictures(AuthorizationSubject subject, List<Long> ids) {
        for (Long pictureId : ids) {
            authorization.checkForUser(PICTURE_VIEW, pictureId, subject.userId());
        }
    }

    private RecipeExecution reject(RecipeExecution execution, String code, String matchedJson,
                                   String quoteJson) {
        return transition(execution, execution.reject(code, matchedJson, quoteJson,
                clock.instant()));
    }

    private RecipeExecution complete(RecipeExecution execution, long taskId, String matchedJson,
                                     String quoteJson) {
        return transition(execution, execution.complete(taskId, matchedJson, quoteJson,
                clock.instant()));
    }

    private RecipeExecution fail(RecipeExecution execution, String code, String matchedJson,
                                 String quoteJson) {
        return transition(execution, execution.fail(code, matchedJson, quoteJson,
                clock.instant()));
    }

    private RecipeExecution transition(RecipeExecution current, RecipeExecution after) {
        if (!executionRepository.transition(after, current.status())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "执行记录发生并发冲突，请重试");
        }
        return after;
    }

    private Recipe requireOwned(AuthorizationSubject subject, long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "配方不存在"));
        if (recipe.subjectId() != subject.userId()) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权操作该配方");
        }
        return recipe;
    }

    private void requireNotDisabled(Recipe recipe) {
        if (recipe.status() == RecipeStatus.DISABLED) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "配方已停用，不能产生新执行");
        }
    }

    private RecipeVersion requireLatestVersion(long recipeId) {
        return versionRepository.findLatest(recipeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR,
                        "配方还没有定义版本，请先发布定义"));
    }

    private long quotedUnits(CreationKind capability) {
        return switch (capability) {
            case STORY_DRAFT ->
                    StoryDraftService.OUTLINE_TRIAL_COST + StoryDraftService.DRAFT_TRIAL_COST;
            case EMOJI_DRAFT -> EmojiDraftService.GENERATE_TRIAL_COST;
            case IMAGE_FUSION -> 0L; // 平台图片创作未开放，融合走 BYOK，不占平台试用额度。
        };
    }

    private String matchedJson(RecipeDefinition definition, Evaluation evaluation) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "when", definition.when().type().name(),
                    "conditions", evaluation.results()));
        } catch (Exception failure) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "执行快照序列化失败");
        }
    }

    /** 图片撤权时的快照：只记 WHEN 类型与"因未授权跳过"，不含任何条件求值结果。 */
    private String unauthorizedSnapshot(RecipeDefinition definition) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "when", definition.when().type().name(),
                    "conditions", List.of(),
                    "conditionResults", "SKIPPED_UNAUTHORIZED"));
        } catch (Exception failure) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "执行快照序列化失败");
        }
    }

    private String quoteJson(CreationKind capability) {
        try {
            java.util.Map<String, Object> quote = new java.util.HashMap<>();
            quote.put("capability", capability.name());
            quote.put("platformUnits", quotedUnits(capability));
            if (capability == CreationKind.IMAGE_FUSION) {
                // 平台图片创作未开放：融合只走用户 BYOK 连接，不占平台试用额度。
                quote.put("byokOnly", true);
            }
            return objectMapper.writeValueAsString(quote);
        } catch (Exception failure) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "报价序列化失败");
        }
    }

    private static String conditionType(RecipeIfCondition condition) {
        if (condition instanceof RecipeIfCondition.SourceSpacePrivate) {
            return "SOURCE_SPACE_PRIVATE";
        }
        if (condition instanceof RecipeIfCondition.SourceCategory) {
            return "SOURCE_CATEGORY";
        }
        return "MAX_TRIAL_COST";
    }

    private static String safeErrorCode(RuntimeException failure) {
        if (failure instanceof ModelInvocationException invocation) {
            return invocation.safeErrorCode();
        }
        if (failure instanceof BusinessException) {
            return "BUSINESS_ERROR";
        }
        return "INTERNAL";
    }

    private record Evaluation(boolean matched, List<Map<String, Object>> results) {
    }
}
