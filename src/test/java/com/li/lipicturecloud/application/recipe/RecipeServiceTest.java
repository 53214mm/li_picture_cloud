package com.li.lipicturecloud.application.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.recipe.view.RecipeDetailView;
import com.li.lipicturecloud.application.recipe.view.RecipeTemplateView;
import com.li.lipicturecloud.domain.recipe.Recipe;
import com.li.lipicturecloud.domain.recipe.RecipeExecutionRepository;
import com.li.lipicturecloud.domain.recipe.RecipeRepository;
import com.li.lipicturecloud.domain.recipe.RecipeStatus;
import com.li.lipicturecloud.domain.recipe.RecipeVersion;
import com.li.lipicturecloud.domain.recipe.RecipeVersionRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
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

class RecipeServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");
    private static final AuthorizationSubject SUBJECT = AuthorizationSubject.user(7L);
    private static final AuthorizationSubject FOREIGN = AuthorizationSubject.user(8L);
    private static final String WHEN_JSON = "{\"type\":\"WEEKLY_REVIEW\"}";
    private static final String IF_JSON = "[{\"type\":\"SOURCE_SPACE_PRIVATE\"}]";
    private static final String THEN_JSON = "{\"capability\":\"STORY_DRAFT\"}";

    private RecipeRepository recipeRepository;
    private RecipeVersionRepository versionRepository;
    private RecipeExecutionRepository executionRepository;
    private RecipeService service;

    @BeforeEach
    void setUp() {
        recipeRepository = mock(RecipeRepository.class);
        versionRepository = mock(RecipeVersionRepository.class);
        executionRepository = mock(RecipeExecutionRepository.class);
        service = new RecipeService(recipeRepository, versionRepository, executionRepository,
                new RecipeDefinitionCodec(new ObjectMapper()),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(recipeRepository.save(any(Recipe.class), anyLong())).thenReturn(true);
    }

    private Recipe recipe(RecipeStatus status, long revision) {
        return Recipe.restore(9L, 7L, "旅行回顾", status, revision, NOW, NOW);
    }

    @Test
    void createInsertsDraftRecipe() {
        when(recipeRepository.insert(any(Recipe.class))).thenAnswer(invocation ->
                invocation.<Recipe>getArgument(0).withId(9L));

        com.li.lipicturecloud.application.recipe.view.RecipeView created =
                service.create(SUBJECT, "旅行回顾");

        assertThat(created.status()).isEqualTo(RecipeStatus.DRAFT.name());
        assertThat(created.name()).isEqualTo("旅行回顾");
        assertThatThrownBy(() -> service.create(SUBJECT, "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("配方名称");
        assertThatThrownBy(() -> service.create(AuthorizationSubject.platformAdmin(1L), "x"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createFromTemplateAppendsFirstVersion() {
        when(recipeRepository.insert(any(Recipe.class))).thenAnswer(invocation ->
                invocation.<Recipe>getArgument(0).withId(9L));
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.DRAFT, 0L)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.empty());
        when(versionRepository.append(any(RecipeVersion.class))).thenAnswer(invocation ->
                invocation.<RecipeVersion>getArgument(0).withId(1L));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of());

        RecipeDetailView detail = service.createFromTemplate(SUBJECT,
                OfficialRecipeTemplates.TRAVEL_REVIEW, "我的旅行配方");

        org.mockito.ArgumentCaptor<Recipe> inserted =
                org.mockito.ArgumentCaptor.forClass(Recipe.class);
        verify(recipeRepository).insert(inserted.capture());
        assertThat(inserted.getValue().name()).isEqualTo("我的旅行配方");
        org.mockito.ArgumentCaptor<RecipeVersion> appended =
                org.mockito.ArgumentCaptor.forClass(RecipeVersion.class);
        verify(versionRepository).append(appended.capture());
        assertThat(appended.getValue().version()).isEqualTo(1);
        assertThat(appended.getValue().thenJson()).contains("STORY_DRAFT");

        assertThatThrownBy(() -> service.createFromTemplate(SUBJECT, "no_such_template", "x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知的官方模板");
    }

    @Test
    void publishDefinitionAppendsNextVersion() throws Exception {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.DRAFT, 0L)));
        when(versionRepository.findLatest(9L)).thenReturn(Optional.of(
                RecipeVersion.restore(2L, 9L, 2, WHEN_JSON, IF_JSON, THEN_JSON, NOW)));
        when(versionRepository.append(any(RecipeVersion.class))).thenAnswer(invocation ->
                invocation.<RecipeVersion>getArgument(0).withId(3L));
        when(versionRepository.findByRecipeId(9L)).thenReturn(List.of(
                RecipeVersion.restore(2L, 9L, 2, WHEN_JSON, IF_JSON, THEN_JSON, NOW)));

        service.publishDefinition(SUBJECT, 9L, new ObjectMapper().readTree("""
                {"when": {"type": "SIMILAR_STORY"}, "conditions": [],
                 "then": {"capability": "IMAGE_FUSION"}}
                """));

        org.mockito.ArgumentCaptor<RecipeVersion> appended =
                org.mockito.ArgumentCaptor.forClass(RecipeVersion.class);
        verify(versionRepository).append(appended.capture());
        assertThat(appended.getValue().version()).isEqualTo(3);
        assertThat(appended.getValue().whenJson()).contains("SIMILAR_STORY");
    }

    @Test
    void enableAndDisableUseCasTransitions() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.DRAFT, 0L)));

        assertThat(service.enable(SUBJECT, 9L).status()).isEqualTo(RecipeStatus.ENABLED.name());
        verify(recipeRepository).save(any(Recipe.class), org.mockito.ArgumentMatchers.eq(0L));

        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.ENABLED, 1L)));
        assertThat(service.disable(SUBJECT, 9L).status()).isEqualTo(RecipeStatus.DISABLED.name());

        // CAS 落败：状态合法但数据库并发冲突 → 友好业务错误。
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.DISABLED, 1L)));
        when(recipeRepository.save(any(Recipe.class), anyLong())).thenReturn(false);
        assertThatThrownBy(() -> service.enable(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("并发冲突");
    }

    @Test
    void deleteCascadesVersionsAndExecutions() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.DISABLED, 2L)));
        when(recipeRepository.deleteById(9L)).thenReturn(true);

        service.delete(SUBJECT, 9L);

        verify(executionRepository).deleteByRecipeId(9L);
        verify(versionRepository).deleteByRecipeId(9L);
        verify(recipeRepository).deleteById(9L);
    }

    @Test
    void ownershipAndExistenceAreEnforced() {
        when(recipeRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(SUBJECT, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("配方不存在");

        when(recipeRepository.findById(9L)).thenReturn(Optional.of(recipe(RecipeStatus.DRAFT, 0L)));
        assertThatThrownBy(() -> service.detail(FOREIGN, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权操作");
    }

    @Test
    void templatesExposeAllFourOfficialRecipes() {
        List<RecipeTemplateView> templates = service.templates();

        assertThat(templates).hasSize(4);
        assertThat(templates).extracting(RecipeTemplateView::code).containsExactlyInAnyOrder(
                OfficialRecipeTemplates.TRAVEL_REVIEW,
                OfficialRecipeTemplates.BIRTHDAY_STORY,
                OfficialRecipeTemplates.WEEKLY_EMOJI,
                OfficialRecipeTemplates.OLD_PHOTO_REMASTER);
        assertThat(templates).allSatisfy(template -> {
            assertThat(template.name()).isNotBlank();
            assertThat(template.thenJson()).isNotBlank();
        });
    }
}
