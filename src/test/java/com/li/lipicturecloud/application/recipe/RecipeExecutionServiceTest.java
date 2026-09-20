package com.li.lipicturecloud.application.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.EmojiDraftService;
import com.li.lipicturecloud.application.airuntime.FusionImageService;
import com.li.lipicturecloud.application.airuntime.LocalCapabilityCatalog;
import com.li.lipicturecloud.application.airuntime.StoryDraftService;
import com.li.lipicturecloud.application.companion.OpportunityContext;
import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationTask;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.ProposalOpportunityType;
import com.li.lipicturecloud.domain.recipe.Recipe;
import com.li.lipicturecloud.domain.recipe.RecipeExecution;
import com.li.lipicturecloud.domain.recipe.RecipeExecutionRepository;
import com.li.lipicturecloud.domain.recipe.RecipeExecutionStatus;
import com.li.lipicturecloud.domain.recipe.RecipeRepository;
import com.li.lipicturecloud.domain.recipe.RecipeStatus;
import com.li.lipicturecloud.domain.recipe.RecipeVersion;
import com.li.lipicturecloud.domain.recipe.RecipeVersionRepository;
import com.li.lipicturecloud.domain.recipe.RecipeWhenType;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.model.entity.Space;
import com.li.lipicturecloud.repository.PictureRepository;
import com.li.lipicturecloud.service.SpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipeExecutionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");
    private static final AuthorizationSubject SUBJECT = AuthorizationSubject.user(7L);
    private static final String KEY = "fef53056-2d9f-467d-9b1d-1afe9a6638fe";
    private static final String WHEN_WEEKLY = "{\"type\":\"WEEKLY_REVIEW\"}";
    private static final String WHEN_ANNIVERSARY = "{\"type\":\"ANNIVERSARY\"}";
    private static final String IF_NONE = "[]";
    private static final String THEN_EMOJI = "{\"capability\":\"EMOJI_DRAFT\"}";
    private static final String THEN_FUSION = "{\"capability\":\"IMAGE_FUSION\"}";
    private static final String THEN_STORY = "{\"capability\":\"STORY_DRAFT\"}";

    private RecipeRepository recipeRepository;
    private RecipeVersionRepository versionRepository;
    private RecipeExecutionRepository executionRepository;
    private StoryDraftService storyDraftService;
    private EmojiDraftService emojiDraftService;
    private FusionImageService fusionImageService;
    private SpaceAuthorizationAccessService authorization;
    private PictureRepository pictureRepository;
    private SpaceService spaceService;
    private GrowthRecordRepository growthRepository;
    private RecipeExecutionService service;

    @BeforeEach
    void setUp() {
        recipeRepository = mock(RecipeRepository.class);
        versionRepository = mock(RecipeVersionRepository.class);
        executionRepository = mock(RecipeExecutionRepository.class);
        storyDraftService = mock(StoryDraftService.class);
        emojiDraftService = mock(EmojiDraftService.class);
        fusionImageService = mock(FusionImageService.class);
        authorization = mock(SpaceAuthorizationAccessService.class);
        pictureRepository = mock(PictureRepository.class);
        spaceService = mock(SpaceService.class);
        growthRepository = mock(GrowthRecordRepository.class);
        service = new RecipeExecutionService(recipeRepository, versionRepository,
                executionRepository, new RecipeDefinitionCodec(new ObjectMapper()),
                new ObjectMapper(), new LocalCapabilityCatalog(), storyDraftService,
                emojiDraftService, fusionImageService,
                authorization, pictureRepository, spaceService, growthRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(executionRepository.transition(any(RecipeExecution.class),
                any(RecipeExecutionStatus.class))).thenReturn(true);
        when(executionRepository.insert(any(RecipeExecution.class))).thenAnswer(invocation ->
                invocation.<RecipeExecution>getArgument(0).withId(5L));
        Picture picture = new Picture();
        picture.setId(102L);
        picture.setCategory("旅行");
        picture.setSpaceId(10L);
        when(pictureRepository.findById(102L)).thenReturn(Optional.of(picture));
    }

    private Recipe recipe(RecipeStatus status) {
        return Recipe.restore(9L, 7L, "旅行回顾", status, 1L, NOW, NOW);
    }

    private RecipeVersion version(int version, String thenJson) {
        return version(version, WHEN_WEEKLY, IF_NONE, thenJson);
    }

    private RecipeVersion version(int version, String whenJson, String ifJson, String thenJson) {
        return RecipeVersion.restore(1L, 9L, version, whenJson, ifJson, thenJson, NOW);
    }

    /** 试运行/待确认记录都带来源图片快照（[102]），确认执行只认这份快照。 */
    private RecipeExecution dryRunRecord() {
        return RecipeExecution.dryRun(9L, 1, 7L, NOW, "{\"when\":\"WEEKLY_REVIEW\"}",
                "{\"platformUnits\":5}", RecipeExecution.snapshotJson(List.of(102L)), NOW)
                .withId(5L);
    }

    private RecipeExecution pendingRecord() {
        return RecipeExecution.pending(9L, 1, 7L, NOW, "{\"when\":\"WEEKLY_REVIEW\"}",
                "{\"platformUnits\":5}", RecipeExecution.snapshotJson(List.of(102L)),
                "WEEKLY_REVIEW-2026-W33", NOW).withId(5L);
    }

    private CreationTask createdTask() {
        return new CreationTask(9L, 7L, CreationKind.STORY_DRAFT, List.of(102L),
                com.li.lipicturecloud.domain.airuntime.CreationStatus.PENDING,
                null, null, null, null, KEY, 0L, NOW, NOW);
    }

    private static String executionKey(long executionId) {
        return java.util.UUID.nameUUIDFromBytes(
                        ("recipe-execution-" + executionId).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
    }

    @Test
    void dryRunRecordsQuoteMatchedSnapshotAndPictureSnapshot() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(version(1, THEN_STORY)));

        RecipeExecution result = service.dryRun(SUBJECT, 9L, List.of(102L));

        assertThat(result.status()).isEqualTo(RecipeExecutionStatus.DRY_RUN);
        assertThat(result.quoteJson()).contains("STORY_DRAFT").contains("5");
        assertThat(result.matchedJson()).contains("WEEKLY_REVIEW");
        // 预览绑定的图片集合必须落库，确认执行时才不会被换掉。
        assertThat(result.sourcePictureIds()).containsExactly(102L);
        assertThat(result.opportunityKey()).isNull();
        verify(authorization).checkForUser("picture:view", 102L, 7L);
    }

    @Test
    void dryRunRejectsDisabledRecipeAndMissingVersion() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.DISABLED)));
        assertThatThrownBy(() -> service.dryRun(SUBJECT, 9L, List.of(102L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已停用");

        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.dryRun(SUBJECT, 9L, List.of(102L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("还没有定义版本");
    }

    @Test
    void dryRunEvaluatesCategoryConditionsFailClosed() {
        String ifCategory = "[{\"type\":\"SOURCE_CATEGORY\",\"category\":\"花园\"}]";
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(
                version(1, WHEN_WEEKLY, ifCategory, THEN_STORY)));

        RecipeExecution result = service.dryRun(SUBJECT, 9L, List.of(102L));

        assertThat(result.matchedJson()).contains("SOURCE_CATEGORY").contains("false");
    }

    /** 未开放能力：试运行必须在读取图片、报价之前就被拒绝，绝不产生"看似可用"的记录。 */
    @Test
    void dryRunRejectsCapabilitiesThatAreNotOpen() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(version(1, THEN_FUSION)));

        assertThatThrownBy(() -> service.dryRun(SUBJECT, 9L, List.of(102L, 103L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未开放");
        assertThatThrownBy(() -> service.dryRun(SUBJECT, 9L, List.of(102L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未开放");
        verify(executionRepository, never()).insert(any(RecipeExecution.class));
        verify(authorization, never()).checkForUser(any(), anyLong(), anyLong());
    }

    @Test
    void executeCompletesWithCreationTaskWhenConditionsMatch() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.of(dryRunRecord()));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(version(1, THEN_STORY)));
        when(storyDraftService.create(SUBJECT, List.of(102L), executionKey(5L)))
                .thenReturn(createdTask());

        RecipeExecution result = service.execute(SUBJECT, 9L, 5L, List.of(102L));

        assertThat(result.status()).isEqualTo(RecipeExecutionStatus.EXECUTED);
        assertThat(result.creationTaskId()).isEqualTo(9L);
        verify(storyDraftService).create(SUBJECT, List.of(102L), executionKey(5L));
    }

    /** P2：确认执行只认记录里的快照；不传图片时直接用快照。 */
    @Test
    void executeUsesTheStoredPictureSnapshotWhenNoPicturesAreSent() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.of(dryRunRecord()));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(version(1, THEN_STORY)));
        when(storyDraftService.create(SUBJECT, List.of(102L), executionKey(5L)))
                .thenReturn(createdTask());

        RecipeExecution result = service.execute(SUBJECT, 9L, 5L, null);

        assertThat(result.status()).isEqualTo(RecipeExecutionStatus.EXECUTED);
        verify(storyDraftService).create(SUBJECT, List.of(102L), executionKey(5L));
    }

    /** P2：改选图片后必须重新试运行，不能拿旧预览确认另一组图片。 */
    @Test
    void executeRejectsAPictureSetDifferentFromTheSnapshot() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.of(dryRunRecord()));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(version(1, THEN_STORY)));

        assertThatThrownBy(() -> service.execute(SUBJECT, 9L, 5L, List.of(103L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重新试运行");
        // 记录仍可确认：只有把图片换成试运行时那一组才允许执行。
        verify(storyDraftService, never()).create(any(), any(), any());
        verify(authorization, never()).checkForUser(any(), anyLong(), anyLong());
    }

    @Test
    void executeRejectsRecordsWithoutAPictureSnapshot() {
        RecipeExecution legacy = RecipeExecution.dryRun(9L, 1, 7L, NOW,
                "{\"when\":\"WEEKLY_REVIEW\"}", "{\"platformUnits\":5}", null, NOW).withId(5L);
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.of(legacy));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(version(1, THEN_STORY)));

        RecipeExecution result = service.execute(SUBJECT, 9L, 5L, null);

        assertThat(result.status()).isEqualTo(RecipeExecutionStatus.REJECTED);
        assertThat(result.safeErrorCode()).isEqualTo(RecipeExecutionService.PICTURE_SET_MISSING);
        verify(storyDraftService, never()).create(any(), any(), any());
    }

    @Test
    void executeRetryReusesTheSameIdempotencyKey() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.of(dryRunRecord()));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(version(1, THEN_STORY)));
        when(storyDraftService.create(SUBJECT, List.of(102L), executionKey(5L)))
                .thenReturn(createdTask());
        // 第一次：任务已创建但执行记录转移冲突（complete 与 fail 的转移都落败）。
        when(executionRepository.transition(any(RecipeExecution.class),
                any(RecipeExecutionStatus.class))).thenReturn(false, false, true);

        assertThatThrownBy(() -> service.execute(SUBJECT, 9L, 5L, List.of(102L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("并发冲突");

        // 重试沿用同一确定性幂等键：创作服务按唯一键去重，绝不产生第二个任务。
        RecipeExecution retried = service.execute(SUBJECT, 9L, 5L, List.of(102L));
        assertThat(retried.status()).isEqualTo(RecipeExecutionStatus.EXECUTED);
        verify(storyDraftService, org.mockito.Mockito.times(2))
                .create(SUBJECT, List.of(102L), executionKey(5L));
    }

    @Test
    void executeRejectsWhenConditionsUnmatch() {
        String ifPrivate = "[{\"type\":\"SOURCE_SPACE_PRIVATE\"}]";
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.of(dryRunRecord()));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(
                version(1, WHEN_WEEKLY, ifPrivate, THEN_STORY)));
        when(spaceService.getById(10L)).thenReturn(null);

        RecipeExecution result = service.execute(SUBJECT, 9L, 5L, List.of(102L));

        assertThat(result.status()).isEqualTo(RecipeExecutionStatus.REJECTED);
        assertThat(result.safeErrorCode()).isEqualTo(RecipeExecutionService.CONDITION_UNMATCHED);
        // 回放快照记录的是执行时求值结果（含未命中的条件），而非试运行快照。
        assertThat(result.matchedJson()).contains("SOURCE_SPACE_PRIVATE").contains("false");
        verify(storyDraftService, never()).create(any(), any(), any());
    }

    /**
     * P1：图片撤权时只记安全错误码——不得先读分类/空间并把条件求值结果写进回放，
     * 也不得把基于未授权图片算出的任何东西落库。
     */
    @Test
    void executeRejectsRevokedPicturesBeforeReadingAnyPictureData() {
        String ifCategory = "[{\"type\":\"SOURCE_CATEGORY\",\"category\":\"旅行\"}]";
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.of(dryRunRecord()));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(
                version(1, WHEN_WEEKLY, ifCategory, THEN_STORY)));
        org.mockito.Mockito.doThrow(new BusinessException(
                        com.li.lipicturecloud.exception.ErrorCode.FORBIDDEN_ERROR, "无权查看该图片"))
                .when(authorization).checkForUser("picture:view", 102L, 7L);

        RecipeExecution result = service.execute(SUBJECT, 9L, 5L, List.of(102L));

        assertThat(result.status()).isEqualTo(RecipeExecutionStatus.REJECTED);
        assertThat(result.safeErrorCode()).isEqualTo(RecipeExecutionService.PICTURE_UNAVAILABLE);
        assertThat(result.matchedJson()).contains("SKIPPED_UNAUTHORIZED");
        assertThat(result.matchedJson()).doesNotContain("SOURCE_CATEGORY");
        // 撤权后绝不读取图片分类或空间信息。
        verify(pictureRepository, never()).findById(anyLong());
        verify(spaceService, never()).getById(anyLong());
        verify(storyDraftService, never()).create(any(), any(), any());
    }

    /** 未开放能力：确认执行同样被拒绝，绝不创建注定无法完成的任务。 */
    @Test
    void executeRejectsCapabilitiesThatAreNotOpen() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.of(dryRunRecord()));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(version(1, THEN_EMOJI)));

        assertThatThrownBy(() -> service.execute(SUBJECT, 9L, 5L, List.of(102L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未开放");
        verify(emojiDraftService, never()).create(any(), any(), any());
        verify(storyDraftService, never()).create(any(), any(), any());
        verify(executionRepository, never()).transition(any(RecipeExecution.class),
                any(RecipeExecutionStatus.class));
    }

    @Test
    void executeRecordsExecutionTimeSnapshotInTheReplay() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.of(dryRunRecord()));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(version(1, THEN_STORY)));
        when(storyDraftService.create(any(), any(), any())).thenReturn(createdTask());

        RecipeExecution result = service.execute(SUBJECT, 9L, 5L, List.of(102L));

        assertThat(result.status()).isEqualTo(RecipeExecutionStatus.EXECUTED);
        assertThat(result.matchedJson()).contains("WEEKLY_REVIEW");
        assertThat(result.quoteJson()).contains("STORY_DRAFT");
    }

    @Test
    void executeFailsWithSafeCodeAndRethrowsOnCreationError() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.of(dryRunRecord()));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(version(1, THEN_STORY)));
        when(storyDraftService.create(any(), any(), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new BusinessException(com.li.lipicturecloud.exception.ErrorCode.OPERATION_ERROR,
                        "任务状态已变化"));

        assertThatThrownBy(() -> service.execute(SUBJECT, 9L, 5L, List.of(102L)))
                .isInstanceOf(BusinessException.class);
        verify(executionRepository).transition(
                org.mockito.ArgumentMatchers.argThat(after ->
                        after.status() == RecipeExecutionStatus.FAILED
                                && "BUSINESS_ERROR".equals(after.safeErrorCode())),
                org.mockito.ArgumentMatchers.eq(RecipeExecutionStatus.DRY_RUN));
    }

    @Test
    void executeRequiresEnabledRecipeAndMatchingExecution() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.DRAFT)));
        assertThatThrownBy(() -> service.execute(SUBJECT, 9L, 5L, List.of(102L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未启用");

        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.execute(SUBJECT, 9L, 5L, List.of(102L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("执行记录不存在");
    }

    @Test
    void recentByRecipeRequiresOwnership() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findRecentByRecipeId(9L, 20)).thenReturn(List.of(dryRunRecord()));

        assertThat(service.recentByRecipe(SUBJECT, 9L, 20)).hasSize(1);
    }

    // ===== 机会触发（阶段 5 的 WHEN 闭环）=====

    /** 机会触发待确认记录：只求值与报价，绝不创建创作任务。 */
    @Test
    void proposeFromOpportunityCreatesAPendingExecutionWithoutCreatingTasks() {
        when(recipeRepository.findEnabledBySubjectId(7L)).thenReturn(List.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(version(1, THEN_STORY)));
        when(growthRepository.findRecentFedPictureIds(3L, 12)).thenReturn(List.of(102L));

        List<RecipeExecution> proposed = service.proposeFromOpportunity(7L, 3L,
                RecipeWhenType.WEEKLY_REVIEW, null, NOW);

        assertThat(proposed).hasSize(1);
        RecipeExecution execution = proposed.get(0);
        assertThat(execution.status()).isEqualTo(RecipeExecutionStatus.PENDING_CONFIRM);
        assertThat(execution.sourcePictureIds()).containsExactly(102L);
        assertThat(execution.opportunityKey()).startsWith("WEEKLY_REVIEW-");
        assertThat(execution.quoteJson()).contains("STORY_DRAFT");
        verify(storyDraftService, never()).create(any(), any(), any());
    }

    /** 相似图片机会携带目标图片：候选集合就是那组新图片，且必须先授权。 */
    @Test
    void proposeFromOpportunityUsesTheOpportunityTargetPictures() {
        when(recipeRepository.findEnabledBySubjectId(7L)).thenReturn(List.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(version(1,
                "{\"type\":\"SIMILAR_STORY\"}", IF_NONE, THEN_STORY)));

        List<RecipeExecution> proposed = service.proposeFromOpportunity(7L, 3L,
                RecipeWhenType.SIMILAR_STORY, List.of(103L), NOW);

        assertThat(proposed).hasSize(1);
        assertThat(proposed.get(0).sourcePictureIds()).containsExactly(103L);
        assertThat(proposed.get(0).opportunityKey()).isEqualTo("SIMILAR_STORY-103");
        verify(authorization).checkForUser("picture:view", 103L, 7L);
        verify(growthRepository, never()).findRecentFedPictureIds(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    /**
     * P1 回归：机会锚点是旧喂养图（旅行），空间里新出现的是花园图。
     * "旅行回顾"必须不命中——IF 条件针对的是本次新图片，而不是那张旧参照图。
     */
    @Test
    void similarOpportunityDoesNotMatchWhenTheNewPictureHasAnotherCategory() {
        when(recipeRepository.findEnabledBySubjectId(7L)).thenReturn(List.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(version(1,
                "{\"type\":\"SIMILAR_STORY\"}",
                "[{\"type\":\"SOURCE_CATEGORY\",\"category\":\"旅行\"}]", THEN_STORY)));
        // 新图片 105 是花园；旧锚点 102 仍然是旅行，但锚点不该参与求值。
        Picture garden = new Picture();
        garden.setId(105L);
        garden.setCategory("花园");
        garden.setSpaceId(10L);
        when(pictureRepository.findById(105L)).thenReturn(Optional.of(garden));

        assertThat(service.proposeFromOpportunity(7L, 3L, RecipeWhenType.SIMILAR_STORY,
                List.of(105L), NOW)).isEmpty();
        verify(executionRepository, never()).insert(any(RecipeExecution.class));
    }

    /** P1 回归：旧喂养图是花园，新出现的是旅行图 → 命中，且任务快照记录的是新旅行图。 */
    @Test
    void similarOpportunityMatchesTheNewPictureAndSnapshotsIt() {
        when(recipeRepository.findEnabledBySubjectId(7L)).thenReturn(List.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(version(1,
                "{\"type\":\"SIMILAR_STORY\"}",
                "[{\"type\":\"SOURCE_CATEGORY\",\"category\":\"旅行\"}]", THEN_STORY)));

        List<RecipeExecution> proposed = service.proposeFromOpportunity(7L, 3L,
                RecipeWhenType.SIMILAR_STORY, List.of(102L), NOW);

        assertThat(proposed).hasSize(1);
        // 新旅行图（102）进入快照；旧喂养图若不同则绝不出现。
        assertThat(proposed.get(0).sourcePictureIds()).containsExactly(102L);
        assertThat(proposed.get(0).opportunityKey()).isEqualTo("SIMILAR_STORY-102");
        assertThat(proposed.get(0).matchedJson()).contains("SOURCE_CATEGORY").contains("true");
    }

    /** 相似图片机会没有目标图片时直接跳过：绝不用旧锚点兜底执行。 */
    @Test
    void similarOpportunityWithoutTargetPicturesIsSkipped() {
        when(recipeRepository.findEnabledBySubjectId(7L)).thenReturn(List.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(version(1,
                "{\"type\":\"SIMILAR_STORY\"}", IF_NONE, THEN_STORY)));

        assertThat(service.proposeFromOpportunity(7L, 3L, RecipeWhenType.SIMILAR_STORY,
                List.of(), NOW)).isEmpty();
        verify(growthRepository, never()).findRecentFedPictureIds(anyLong(),
                org.mockito.ArgumentMatchers.anyInt());
        verify(executionRepository, never()).insert(any(RecipeExecution.class));
    }

    /** 未开放能力不得被机会触发成"等待确认"的假机会。 */
    @Test
    void proposeFromOpportunitySkipsCapabilitiesThatAreNotOpen() {
        when(recipeRepository.findEnabledBySubjectId(7L)).thenReturn(List.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(version(1, THEN_EMOJI)));

        List<RecipeExecution> proposed = service.proposeFromOpportunity(7L, 3L,
                RecipeWhenType.WEEKLY_REVIEW, List.of(102L), NOW);

        assertThat(proposed).isEmpty();
        verify(executionRepository, never()).insert(any(RecipeExecution.class));
    }

    @Test
    void proposeFromOpportunitySkipsRecipesWithADifferentWhen() {
        when(recipeRepository.findEnabledBySubjectId(7L)).thenReturn(List.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(
                version(1, WHEN_ANNIVERSARY, IF_NONE, THEN_STORY)));

        assertThat(service.proposeFromOpportunity(7L, 3L, RecipeWhenType.WEEKLY_REVIEW,
                List.of(102L), NOW)).isEmpty();
        verify(executionRepository, never()).insert(any(RecipeExecution.class));
    }

    /** 同一机会窗口只产生一条待确认记录；已有待确认记录时不再叠加。 */
    @Test
    void proposeFromOpportunityDeduplicatesWithinTheSameOpportunityWindow() {
        when(recipeRepository.findEnabledBySubjectId(7L)).thenReturn(List.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(version(1, THEN_STORY)));
        when(growthRepository.findRecentFedPictureIds(3L, 12)).thenReturn(List.of(102L));
        when(executionRepository.findRecentByRecipeId(9L, 20)).thenReturn(List.of(pendingRecord()));

        assertThat(service.proposeFromOpportunity(7L, 3L, RecipeWhenType.WEEKLY_REVIEW, List.of(),
                NOW)).isEmpty();

        when(executionRepository.findRecentByRecipeId(9L, 20)).thenReturn(List.of());
        when(executionRepository.findAwaitingConfirm(9L)).thenReturn(Optional.of(dryRunRecord()));
        assertThat(service.proposeFromOpportunity(7L, 3L, RecipeWhenType.WEEKLY_REVIEW, List.of(),
                NOW)).isEmpty();
        verify(executionRepository, never()).insert(any(RecipeExecution.class));
    }

    /** 数据库唯一索引是最终仲裁：并发下输的一方插入冲突时直接跳过，不冒泡成 500。 */
    @Test
    void proposeFromOpportunitySkipsWhenTheUniqueIndexRejectsTheInsert() {
        when(recipeRepository.findEnabledBySubjectId(7L)).thenReturn(List.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(version(1, THEN_STORY)));
        when(growthRepository.findRecentFedPictureIds(3L, 12)).thenReturn(List.of(102L));
        when(executionRepository.insert(any(RecipeExecution.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("dup"));

        assertThat(service.proposeFromOpportunity(7L, 3L, RecipeWhenType.WEEKLY_REVIEW, List.of(),
                NOW)).isEmpty();
    }

    /** 候选图片全部撤权：不产生待确认记录（也不读取分类/空间）。 */
    @Test
    void proposeFromOpportunitySkipsWhenNoCandidatePictureIsAuthorized() {
        when(recipeRepository.findEnabledBySubjectId(7L)).thenReturn(List.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(version(1, THEN_STORY)));
        when(growthRepository.findRecentFedPictureIds(3L, 12)).thenReturn(List.of(102L));
        org.mockito.Mockito.doThrow(new BusinessException(
                        com.li.lipicturecloud.exception.ErrorCode.FORBIDDEN_ERROR, "无权查看该图片"))
                .when(authorization).checkForUser("picture:view", 102L, 7L);

        assertThat(service.proposeFromOpportunity(7L, 3L, RecipeWhenType.WEEKLY_REVIEW, List.of(),
                NOW)).isEmpty();
        verify(pictureRepository, never()).findById(anyLong());
        verify(executionRepository, never()).insert(any(RecipeExecution.class));
    }

    /** 机会触发的待确认记录走同一条确认路径（PENDING_CONFIRM → EXECUTED）。 */
    @Test
    void executeConfirmsAnOpportunityProposedRecord() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.of(pendingRecord()));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(version(1, THEN_STORY)));
        when(storyDraftService.create(SUBJECT, List.of(102L), executionKey(5L)))
                .thenReturn(createdTask());

        RecipeExecution result = service.execute(SUBJECT, 9L, 5L, List.of());

        assertThat(result.status()).isEqualTo(RecipeExecutionStatus.EXECUTED);
        verify(executionRepository).transition(any(RecipeExecution.class),
                org.mockito.ArgumentMatchers.eq(RecipeExecutionStatus.PENDING_CONFIRM));
    }

    /**
     * P1 端到端形状：机会上下文同时带锚点与目标（真实仓储可能返回 [锚点, 新图]），
     * 配方必须丢弃锚点——旧花园锚点不得污染"新旅行图"的命中判断，也不能进入执行快照。
     */
    @Test
    void onOpportunityDropsTheAnchorFromTheTargetPictures() {
        Picture garden = new Picture();
        garden.setId(102L);
        garden.setCategory("花园");
        garden.setSpaceId(10L);
        when(pictureRepository.findById(102L)).thenReturn(Optional.of(garden));
        Picture travel = new Picture();
        travel.setId(103L);
        travel.setCategory("旅行");
        travel.setSpaceId(10L);
        when(pictureRepository.findById(103L)).thenReturn(Optional.of(travel));
        when(recipeRepository.findEnabledBySubjectId(7L)).thenReturn(List.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(version(1,
                "{\"type\":\"SIMILAR_STORY\"}",
                "[{\"type\":\"SOURCE_CATEGORY\",\"category\":\"旅行\"}]", THEN_STORY)));

        service.onOpportunity(7L, 3L, new OpportunityContext(
                ProposalOpportunityType.SIMILAR_STORY, 102L, List.of(102L, 103L), 10L, 2L), NOW);

        org.mockito.ArgumentCaptor<RecipeExecution> inserted =
                org.mockito.ArgumentCaptor.forClass(RecipeExecution.class);
        verify(executionRepository).insert(inserted.capture());
        RecipeExecution proposed = inserted.getValue();
        // 快照只有新旅行图（103）；锚点花园图（102）既不参与求值也不进任务。
        assertThat(proposed.sourcePictureIds()).containsExactly(103L);
        assertThat(proposed.opportunityKey()).isEqualTo("SIMILAR_STORY-103");
        // 锚点分类没有被读取（它是参照图，不是目标）。
        verify(pictureRepository, never()).findById(102L);
    }

    /** 端口映射：强类型机会 + 目标图片进入候选；未知类型只忽略，不打断任何链路。 */
    @Test
    void onOpportunityMapsTheTypedContextAndIgnoresUnknownTypes() {
        when(recipeRepository.findEnabledBySubjectId(7L)).thenReturn(List.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(version(1,
                "{\"type\":\"SIMILAR_STORY\"}", IF_NONE, THEN_STORY)));

        service.onOpportunity(7L, 3L, new OpportunityContext(
                ProposalOpportunityType.SIMILAR_STORY, 999L, List.of(102L), 10L, 3L), NOW);

        verify(executionRepository, org.mockito.Mockito.times(1))
                .insert(any(RecipeExecution.class));

        // 未知机会类型（未来机会源）与 null 上下文都只忽略，不抛错。
        service.onOpportunity(7L, 3L, null, NOW);
        verify(executionRepository, org.mockito.Mockito.times(1))
                .insert(any(RecipeExecution.class));
    }
}
