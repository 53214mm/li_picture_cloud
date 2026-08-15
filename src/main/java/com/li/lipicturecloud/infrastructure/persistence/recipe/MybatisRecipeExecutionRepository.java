package com.li.lipicturecloud.infrastructure.persistence.recipe;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.lipicturecloud.domain.recipe.RecipeExecution;
import com.li.lipicturecloud.domain.recipe.RecipeExecutionRepository;
import com.li.lipicturecloud.domain.recipe.RecipeExecutionStatus;
import com.li.lipicturecloud.mapper.RecipeExecutionMapper;
import com.li.lipicturecloud.model.entity.RecipeExecutionEntity;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisRecipeExecutionRepository implements RecipeExecutionRepository {

    private final RecipeExecutionMapper executionMapper;

    public MybatisRecipeExecutionRepository(RecipeExecutionMapper executionMapper) {
        this.executionMapper = executionMapper;
    }

    @Override
    public Optional<RecipeExecution> findById(long id) {
        if (id <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(executionMapper.selectById(id)).map(this::fromRow);
    }

    @Override
    public List<RecipeExecution> findRecentByRecipeId(long recipeId, int limit) {
        if (recipeId <= 0) {
            return List.of();
        }
        return executionMapper.selectList(new LambdaQueryWrapper<RecipeExecutionEntity>()
                        .eq(RecipeExecutionEntity::getRecipeId, recipeId)
                        .orderByDesc(RecipeExecutionEntity::getCreatedTime)
                        .orderByDesc(RecipeExecutionEntity::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100))))
                .stream().map(this::fromRow).toList();
    }

    @Override
    public List<RecipeExecution> findRecentBySubjectId(long subjectId, int limit) {
        if (subjectId <= 0) {
            return List.of();
        }
        return executionMapper.selectList(new LambdaQueryWrapper<RecipeExecutionEntity>()
                        .eq(RecipeExecutionEntity::getSubjectId, subjectId)
                        .orderByDesc(RecipeExecutionEntity::getCreatedTime)
                        .orderByDesc(RecipeExecutionEntity::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100))))
                .stream().map(this::fromRow).toList();
    }

    @Override
    public RecipeExecution insert(RecipeExecution execution) {
        Objects.requireNonNull(execution, "execution");
        if (execution.id() != null) {
            throw new IllegalArgumentException("cannot insert an already persisted recipe execution");
        }
        RecipeExecutionEntity row = toRow(execution);
        executionMapper.insert(row);
        return execution.withId(Objects.requireNonNull(row.getId(), "assigned execution id"));
    }

    @Override
    public boolean transition(RecipeExecution after, RecipeExecutionStatus expectedStatus) {
        Objects.requireNonNull(after, "execution");
        Objects.requireNonNull(expectedStatus, "expectedStatus");
        if (after.id() == null) {
            throw new IllegalArgumentException("cannot transition an unpersisted recipe execution");
        }
        if (!after.isTerminal() || after.status() == expectedStatus) {
            throw new IllegalArgumentException("execution transition must move to a different terminal state");
        }
        UpdateWrapper<RecipeExecutionEntity> update = new UpdateWrapper<>();
        update.eq("id", after.id())
                .eq("status", expectedStatus.name())
                .set("status", after.status().name())
                .set("matchedJson", after.matchedJson())
                .set("quoteJson", after.quoteJson())
                .set("creationTaskId", after.creationTaskId())
                .set("safeErrorCode", after.safeErrorCode());
        return executionMapper.update(null, update) == 1;
    }

    @Override
    public int deleteByRecipeId(long recipeId) {
        if (recipeId <= 0) {
            return 0;
        }
        return executionMapper.delete(new LambdaQueryWrapper<RecipeExecutionEntity>()
                .eq(RecipeExecutionEntity::getRecipeId, recipeId));
    }

    private RecipeExecution fromRow(RecipeExecutionEntity row) {
        return RecipeExecution.restore(row.getId(), row.getRecipeId(), row.getRecipeVersion(),
                row.getSubjectId(), RecipeExecutionStatus.valueOf(row.getStatus()),
                Objects.requireNonNull(row.getTriggeredTime(), "triggeredTime").toInstant(),
                row.getMatchedJson(), row.getQuoteJson(), row.getCreationTaskId(),
                row.getSafeErrorCode(),
                Objects.requireNonNull(row.getCreatedTime(), "createdTime").toInstant());
    }

    private RecipeExecutionEntity toRow(RecipeExecution execution) {
        RecipeExecutionEntity row = new RecipeExecutionEntity();
        row.setId(execution.id());
        row.setRecipeId(execution.recipeId());
        row.setRecipeVersion(execution.recipeVersion());
        row.setSubjectId(execution.subjectId());
        row.setStatus(execution.status().name());
        row.setTriggeredTime(Date.from(execution.triggeredTime()));
        row.setMatchedJson(execution.matchedJson());
        row.setQuoteJson(execution.quoteJson());
        row.setCreationTaskId(execution.creationTaskId());
        row.setSafeErrorCode(execution.safeErrorCode());
        row.setCreatedTime(Date.from(execution.createdTime()));
        return row;
    }
}
