package com.li.lipicturecloud.domain.recipe;

import java.util.List;
import java.util.Optional;

/**
 * 配方执行记录的持久化端口：写入一次，终态转移以原状态为 CAS 条件。
 */
public interface RecipeExecutionRepository {

    Optional<RecipeExecution> findById(long id);

    List<RecipeExecution> findRecentByRecipeId(long recipeId, int limit);

    List<RecipeExecution> findRecentBySubjectId(long subjectId, int limit);

    RecipeExecution insert(RecipeExecution execution);

    boolean transition(RecipeExecution after, RecipeExecutionStatus expectedStatus);

    /**
     * 该配方当前仍等待用户确认的执行记录（DRY_RUN 或 PENDING_CONFIRM），
     * 用于机会触发的去重：同一配方已有待确认记录时不再重复产生。
     */
    Optional<RecipeExecution> findAwaitingConfirm(long recipeId);

    int deleteByRecipeId(long recipeId);
}
