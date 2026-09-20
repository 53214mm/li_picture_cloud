package com.li.lipicturecloud.infrastructure.persistence.recipe;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.lipicturecloud.domain.recipe.Recipe;
import com.li.lipicturecloud.domain.recipe.RecipeRepository;
import com.li.lipicturecloud.domain.recipe.RecipeStatus;
import com.li.lipicturecloud.mapper.RecipeMapper;
import com.li.lipicturecloud.model.entity.RecipeEntity;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisRecipeRepository implements RecipeRepository {

    private final RecipeMapper recipeMapper;

    public MybatisRecipeRepository(RecipeMapper recipeMapper) {
        this.recipeMapper = recipeMapper;
    }

    @Override
    public Optional<Recipe> findById(long id) {
        if (id <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(recipeMapper.selectById(id)).map(this::fromRow);
    }

    @Override
    public List<Recipe> findBySubjectId(long subjectId, int limit) {
        return recipeMapper.selectList(new LambdaQueryWrapper<RecipeEntity>()
                        .eq(RecipeEntity::getSubjectId, subjectId)
                        .orderByDesc(RecipeEntity::getUpdateTime)
                        .orderByDesc(RecipeEntity::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100))))
                .stream().map(this::fromRow).toList();
    }

    @Override
    public List<Recipe> findEnabledBySubjectId(long subjectId) {
        return recipeMapper.selectList(new LambdaQueryWrapper<RecipeEntity>()
                        .eq(RecipeEntity::getSubjectId, subjectId)
                        .eq(RecipeEntity::getStatus, RecipeStatus.ENABLED.name()))
                .stream().map(this::fromRow).toList();
    }

    @Override
    public Recipe insert(Recipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        if (recipe.id() != null) {
            throw new IllegalArgumentException("cannot insert an already persisted recipe");
        }
        RecipeEntity row = toRow(recipe);
        recipeMapper.insert(row);
        return recipe.withId(Objects.requireNonNull(row.getId(), "assigned recipe id"));
    }

    @Override
    public boolean save(Recipe after, long expectedRevision) {
        Objects.requireNonNull(after, "recipe");
        if (after.id() == null) {
            throw new IllegalArgumentException("cannot save an unpersisted recipe");
        }
        if (expectedRevision < 0 || after.revision() != Math.addExact(expectedRevision, 1L)) {
            throw new IllegalArgumentException("recipe revision must advance by exactly one");
        }
        UpdateWrapper<RecipeEntity> update = new UpdateWrapper<>();
        update.eq("id", after.id())
                .eq("revision", expectedRevision)
                .set("status", after.status().name())
                .set("revision", after.revision())
                .set("updateTime", Date.from(after.updatedTime()));
        return recipeMapper.update(null, update) == 1;
    }

    @Override
    public boolean deleteById(long id) {
        if (id <= 0) {
            return false;
        }
        return recipeMapper.deleteById(id) == 1;
    }

    private Recipe fromRow(RecipeEntity row) {
        return Recipe.restore(row.getId(), row.getSubjectId(), row.getName(),
                RecipeStatus.valueOf(row.getStatus()), row.getRevision(),
                Objects.requireNonNull(row.getCreateTime(), "createTime").toInstant(),
                Objects.requireNonNull(row.getUpdateTime(), "updateTime").toInstant());
    }

    private RecipeEntity toRow(Recipe recipe) {
        RecipeEntity row = new RecipeEntity();
        row.setId(recipe.id());
        row.setSubjectId(recipe.subjectId());
        row.setName(recipe.name());
        row.setStatus(recipe.status().name());
        row.setRevision(recipe.revision());
        row.setCreateTime(Date.from(recipe.createdTime()));
        row.setUpdateTime(Date.from(recipe.updatedTime()));
        return row;
    }
}
