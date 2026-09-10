package com.li.lipicturecloud.infrastructure.persistence.recipe;

import com.li.lipicturecloud.domain.recipe.Recipe;
import com.li.lipicturecloud.domain.recipe.RecipeExecution;
import com.li.lipicturecloud.domain.recipe.RecipeExecutionRepository;
import com.li.lipicturecloud.domain.recipe.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 配方执行记录的机会去重依赖数据库唯一索引（recipeId, opportunityKey）：
 * 应用层"先查再插"在并发下会双插，唯一索引才是最终仲裁。
 * 同时锁定 NULL 语义：手工试运行/历史记录的 opportunityKey 为 NULL，
 * 同一配方必须能存多条（H2 与 MySQL 的唯一索引都把 NULL 视为互不相同）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MybatisRecipeExecutionRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-15T08:00:00Z");

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private RecipeExecutionRepository executionRepository;

    @Test
    void opportunityKeyIsUniquePerRecipeWhileNullKeysCoexist() {
        Recipe recipe = recipeRepository.insert(Recipe.create(7L, "回顾配方", NOW));
        Recipe other = recipeRepository.insert(Recipe.create(7L, "另一个配方", NOW));

        // 同一配方两条手工试运行（opportunityKey 为 NULL）必须都能落库。
        RecipeExecution firstManual = executionRepository.insert(
                RecipeExecution.dryRun(recipe.id(), 1, 7L, NOW, "{\"when\":\"WEEKLY_REVIEW\"}",
                        "{\"platformUnits\":5}", RecipeExecution.snapshotJson(List.of(102L)), NOW));
        RecipeExecution secondManual = executionRepository.insert(
                RecipeExecution.dryRun(recipe.id(), 1, 7L, NOW, "{\"when\":\"WEEKLY_REVIEW\"}",
                        "{\"platformUnits\":5}", RecipeExecution.snapshotJson(List.of(103L)), NOW));
        assertThat(firstManual.id()).isNotNull();
        assertThat(secondManual.id()).isNotNull();

        // 同一机会窗口的第一条待确认记录可以落库……
        String key = "WEEKLY_REVIEW-2026-W33";
        executionRepository.insert(RecipeExecution.pending(recipe.id(), 1, 7L, NOW,
                "{\"when\":\"WEEKLY_REVIEW\"}", "{\"platformUnits\":5}",
                RecipeExecution.snapshotJson(List.of(102L)), key, NOW));

        // ……同一配方 + 同一机会键的第二条必须被数据库拒绝（并发双插的最后一道闸）。
        assertThatThrownBy(() -> executionRepository.insert(RecipeExecution.pending(recipe.id(), 1,
                7L, NOW, "{\"when\":\"WEEKLY_REVIEW\"}", "{\"platformUnits\":5}",
                RecipeExecution.snapshotJson(List.of(102L)), key, NOW)))
                .isInstanceOf(DuplicateKeyException.class);

        // 不同配方的同一机会键互不影响。
        executionRepository.insert(RecipeExecution.pending(other.id(), 1, 7L, NOW,
                "{\"when\":\"WEEKLY_REVIEW\"}", "{\"platformUnits\":5}",
                RecipeExecution.snapshotJson(List.of(102L)), key, NOW));
        assertThat(executionRepository.findRecentBySubjectId(7L, 20)).hasSize(4);
    }
}
