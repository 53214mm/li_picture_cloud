package com.li.lipicturecloud.application.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.EmojiDraftService;
import com.li.lipicturecloud.application.airuntime.FusionImageService;
import com.li.lipicturecloud.application.airuntime.StoryDraftService;
import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationTask;
import com.li.lipicturecloud.domain.recipe.Recipe;
import com.li.lipicturecloud.domain.recipe.RecipeExecution;
import com.li.lipicturecloud.domain.recipe.RecipeExecutionRepository;
import com.li.lipicturecloud.domain.recipe.RecipeExecutionStatus;
import com.li.lipicturecloud.domain.recipe.RecipeRepository;
import com.li.lipicturecloud.domain.recipe.RecipeStatus;
import com.li.lipicturecloud.domain.recipe.RecipeVersion;
import com.li.lipicturecloud.domain.recipe.RecipeVersionRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipeExecutionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");
    private static final AuthorizationSubject SUBJECT = AuthorizationSubject.user(7L);
    private static final String KEY = "fef53056-2d9f-467d-9b1d-1afe9a6638fe";
    private static final String WHEN_WEEKLY = "{\"type\":\"WEEKLY_REVIEW\"}";
    private static final String IF_NONE = "[]";
    private static final String THEN_EMOJI = "{\"capability\":\"EMOJI_DRAFT\"}";
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
        service = new RecipeExecutionService(recipeRepository, versionRepository,
                executionRepository, new RecipeDefinitionCodec(new ObjectMapper()),
                new ObjectMapper(), storyDraftService, emojiDraftService, fusionImageService,
                authorization, pictureRepository, spaceService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(executionRepository.transition(any(RecipeExecution.class),
                any(RecipeExecutionStatus.class))).thenReturn(true);
        Picture picture = new Picture();
        picture.setId(102L);
        picture.setCategory("旅行");
        picture.setSpaceId(10L);
        when(pictureRepository.findById(102L)).thenReturn(Optional.of(picture));
    }

    private Recipe recipe(RecipeStatus status) {
        return Recipe.restore(9L, 7L, "每周表情", status, 1L, NOW, NOW);
    }

    private RecipeVersion version(int version, String thenJson) {
        return RecipeVersion.restore(1L, 9L, version, WHEN_WEEKLY, IF_NONE, thenJson, NOW);
    }

    private RecipeExecution dryRunRecord() {
        return RecipeExecution.dryRun(9L, 1, 7L, NOW, "{\"when\":\"WEEKLY_REVIEW\"}",
                "{\"platformUnits\":1}", NOW).withId(5L);
    }

    @Test
    void dryRunRecordsQuoteAndMatchedSnapshot() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(version(1, THEN_EMOJI)));
        when(executionRepository.insert(any(RecipeExecution.class))).thenAnswer(invocation ->
                invocation.<RecipeExecution>getArgument(0).withId(5L));

        RecipeExecution result = service.dryRun(SUBJECT, 9L, List.of(102L));

        assertThat(result.status()).isEqualTo(RecipeExecutionStatus.DRY_RUN);
        assertThat(result.quoteJson()).contains("EMOJI_DRAFT").contains("1");
        assertThat(result.matchedJson()).contains("WEEKLY_REVIEW");
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
                RecipeVersion.restore(1L, 9L, 1, WHEN_WEEKLY, ifCategory, THEN_EMOJI, NOW)));
        when(executionRepository.insert(any(RecipeExecution.class))).thenAnswer(invocation ->
                invocation.<RecipeExecution>getArgument(0).withId(5L));

        RecipeExecution result = service.dryRun(SUBJECT, 9L, List.of(102L));

        assertThat(result.matchedJson()).contains("SOURCE_CATEGORY").contains("false");
    }

    @Test
    void dryRunRequiresTwoPicturesForFusion() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(version(1,
                "{\"capability\":\"IMAGE_FUSION\"}")));

        assertThatThrownBy(() -> service.dryRun(SUBJECT, 9L, List.of(102L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要 2 张");
    }

    @Test
    void executeCompletesWithCreationTaskWhenConditionsMatch() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.of(dryRunRecord()));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(version(1, THEN_STORY)));
        CreationTask task = new CreationTask(9L, 7L, CreationKind.STORY_DRAFT,
                List.of(102L), com.li.lipicturecloud.domain.airuntime.CreationStatus.PENDING,
                null, null, null, null, KEY, 0L, NOW, NOW);
        String expectedKey = java.util.UUID.nameUUIDFromBytes(
                        "recipe-execution-5".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
        when(storyDraftService.create(SUBJECT, List.of(102L), expectedKey)).thenReturn(task);

        RecipeExecution result = service.execute(SUBJECT, 9L, 5L, List.of(102L));

        assertThat(result.status()).isEqualTo(RecipeExecutionStatus.EXECUTED);
        assertThat(result.creationTaskId()).isEqualTo(9L);
        verify(storyDraftService).create(SUBJECT, List.of(102L), expectedKey);
    }

    @Test
    void executeRetryReusesTheSameIdempotencyKey() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.of(dryRunRecord()));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(version(1, THEN_STORY)));
        CreationTask task = new CreationTask(9L, 7L, CreationKind.STORY_DRAFT,
                List.of(102L), com.li.lipicturecloud.domain.airuntime.CreationStatus.PENDING,
                null, null, null, null, KEY, 0L, NOW, NOW);
        String expectedKey = java.util.UUID.nameUUIDFromBytes(
                        "recipe-execution-5".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
        when(storyDraftService.create(SUBJECT, List.of(102L), expectedKey)).thenReturn(task);
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
                .create(SUBJECT, List.of(102L), expectedKey);
    }

    @Test
    void executeRejectsWhenConditionsUnmatch() {
        String ifPrivate = "[{\"type\":\"SOURCE_SPACE_PRIVATE\"}]";
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.of(dryRunRecord()));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(
                RecipeVersion.restore(1L, 9L, 1, WHEN_WEEKLY, ifPrivate, THEN_STORY, NOW)));
        when(spaceService.getById(10L)).thenReturn(null);

        RecipeExecution result = service.execute(SUBJECT, 9L, 5L, List.of(102L));

        assertThat(result.status()).isEqualTo(RecipeExecutionStatus.REJECTED);
        assertThat(result.safeErrorCode()).isEqualTo(RecipeExecutionService.CONDITION_UNMATCHED);
        // 回放快照记录的是执行时求值结果（含未命中的条件），而非试运行快照。
        assertThat(result.matchedJson()).contains("SOURCE_SPACE_PRIVATE").contains("false");
        verify(storyDraftService, never()).create(any(), any(), any());
    }

    @Test
    void executeRejectsWhenPictureAuthorizationIsRevoked() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.of(dryRunRecord()));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(version(1, THEN_STORY)));
        org.mockito.Mockito.doThrow(new BusinessException(
                        com.li.lipicturecloud.exception.ErrorCode.FORBIDDEN_ERROR, "无权查看该图片"))
                .when(authorization).checkForUser("picture:view", 102L, 7L);

        RecipeExecution result = service.execute(SUBJECT, 9L, 5L, List.of(102L));

        assertThat(result.status()).isEqualTo(RecipeExecutionStatus.REJECTED);
        assertThat(result.safeErrorCode()).isEqualTo("PICTURE_UNAVAILABLE");
        verify(storyDraftService, never()).create(any(), any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void executeRecordsExecutionTimeSnapshotInTheReplay() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED)));
        when(executionRepository.findById(5L)).thenReturn(Optional.of(dryRunRecord()));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(version(1, THEN_STORY)));
        CreationTask task = new CreationTask(9L, 7L, CreationKind.STORY_DRAFT,
                List.of(102L), com.li.lipicturecloud.domain.airuntime.CreationStatus.PENDING,
                null, null, null, null, KEY, 0L, NOW, NOW);
        String expectedKey = java.util.UUID.nameUUIDFromBytes(
                        "recipe-execution-5".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
        when(storyDraftService.create(SUBJECT, List.of(102L), expectedKey)).thenReturn(task);

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
}
