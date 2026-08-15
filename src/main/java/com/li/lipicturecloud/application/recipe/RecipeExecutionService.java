package com.li.lipicturecloud.application.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.EmojiDraftService;
import com.li.lipicturecloud.application.airuntime.FusionImageService;
import com.li.lipicturecloud.application.airuntime.ModelInvocationException;
import com.li.lipicturecloud.application.airuntime.StoryDraftService;
import com.li.lipicturecloud.application.recipe.view.RecipeExecutionView;
import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationTask;
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
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.model.entity.Space;
import com.li.lipicturecloud.repository.PictureRepository;
import com.li.lipicturecloud.service.SpaceService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;

/**
 * 配方执行引擎：试运行（评估 WHEN/IF + 报价，只落 DRY_RUN 记录）与真实执行
 * （重新按记录版本校验 → IF 求值 → 调用白名单能力创建创作任务 → 终态转移）。
 *
 * <p>执行前重新校验 PICTURE_VIEW（收紧不扩大）；DISABLED 不产生新执行；
 * 条件不满足 → REJECTED，执行失败只携带安全错误码；报价是平台试用额度上限承诺，
 * 实际结算仍由各创作服务的试用账本硬上限守护。</p>
 */
@Service
public class RecipeExecutionService {

    public static final String CONDITION_UNMATCHED = "CONDITION_UNMATCHED";
    private static final int PRIVATE_SPACE_TYPE = 0;

    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository versionRepository;
    private final RecipeExecutionRepository executionRepository;
    private final RecipeDefinitionCodec codec;
    private final ObjectMapper objectMapper;
    private final StoryDraftService storyDraftService;
    private final EmojiDraftService emojiDraftService;
    private final FusionImageService fusionImageService;
    private final SpaceAuthorizationAccessService authorization;
    private final PictureRepository pictureRepository;
    private final SpaceService spaceService;
    private final Clock clock;

    public RecipeExecutionService(RecipeRepository recipeRepository,
                                  RecipeVersionRepository versionRepository,
                                  RecipeExecutionRepository executionRepository,
                                  RecipeDefinitionCodec codec,
                                  ObjectMapper objectMapper,
                                  StoryDraftService storyDraftService,
                                  EmojiDraftService emojiDraftService,
                                  FusionImageService fusionImageService,
                                  SpaceAuthorizationAccessService authorization,
                                  PictureRepository pictureRepository,
                                  SpaceService spaceService,
                                  Clock clock) {
        this.recipeRepository = recipeRepository;
        this.versionRepository = versionRepository;
        this.executionRepository = executionRepository;
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.storyDraftService = storyDraftService;
        this.emojiDraftService = emojiDraftService;
        this.fusionImageService = fusionImageService;
        this.authorization = authorization;
        this.pictureRepository = pictureRepository;
        this.spaceService = spaceService;
        this.clock = clock;
    }

    public RecipeExecution dryRun(AuthorizationSubject subject, long recipeId,
                                  List<Long> pictureIds) {
        Recipe recipe = requireOwned(subject, recipeId);
        requireNotDisabled(recipe);
        RecipeVersion version = requireLatestVersion(recipe.id());
        RecipeDefinition definition = codec.decode(version.whenJson(), version.ifJson(),
                version.thenJson());
        List<Long> ids = requireValidPictureIds(definition.then().capability(), pictureIds);
        reauthorizePictures(subject, ids);
        Instant now = clock.instant();
        return executionRepository.insert(RecipeExecution.dryRun(recipe.id(), version.version(),
                subject.userId(), now,
                matchedJson(definition, evaluate(definition, ids, subject.userId())),
                quoteJson(definition.then().capability()), now));
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
                || execution.status() != RecipeExecutionStatus.DRY_RUN) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "执行记录状态不可用");
        }
        RecipeVersion version = versionRepository.findByRecipeId(recipe.id()).stream()
                .filter(candidate -> candidate.version() == execution.recipeVersion())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR, "配方版本已失效"));
        RecipeDefinition definition = codec.decode(version.whenJson(), version.ifJson(),
                version.thenJson());
        List<Long> ids = requireValidPictureIds(definition.then().capability(), pictureIds);
        // 执行时按当前图片集合重新求值并快照，回放记录的是执行时结果而非试运行快照。
        Evaluation evaluation = evaluate(definition, ids, subject.userId());
        String matchedJson = matchedJson(definition, evaluation);
        String quoteJson = quoteJson(definition.then().capability());
        try {
            reauthorizePictures(subject, ids);
        } catch (BusinessException unavailable) {
            // 图片撤权/不可用：执行记录转入 REJECTED，不留悬空 DRY_RUN。
            return reject(execution, "PICTURE_UNAVAILABLE", matchedJson, quoteJson);
        }
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
                execution.matchedJson(), execution.quoteJson(), execution.creationTaskId(),
                execution.safeErrorCode(), execution.createdTime());
    }

    // ===== 内部求值与执行 =====

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
        if (pictureIds.size() > 12) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "一次执行最多 12 张图片");
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
