package com.li.lipicturecloud.infrastructure.persistence.recipe;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.lipicturecloud.domain.recipe.RecipeVersion;
import com.li.lipicturecloud.domain.recipe.RecipeVersionRepository;
import com.li.lipicturecloud.mapper.RecipeVersionMapper;
import com.li.lipicturecloud.model.entity.RecipeVersionEntity;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisRecipeVersionRepository implements RecipeVersionRepository {

    private final RecipeVersionMapper versionMapper;

    public MybatisRecipeVersionRepository(RecipeVersionMapper versionMapper) {
        this.versionMapper = versionMapper;
    }

    @Override
    public RecipeVersion append(RecipeVersion version) {
        Objects.requireNonNull(version, "version");
        if (version.id() != null) {
            throw new IllegalArgumentException("cannot append an already persisted recipe version");
        }
        RecipeVersionEntity row = new RecipeVersionEntity();
        row.setRecipeId(version.recipeId());
        row.setVersion(version.version());
        row.setWhenJson(version.whenJson());
        row.setIfJson(version.ifJson());
        row.setThenJson(version.thenJson());
        row.setCreatedTime(Date.from(version.createdTime()));
        versionMapper.insert(row);
        return version.withId(Objects.requireNonNull(row.getId(), "assigned recipe version id"));
    }

    @Override
    public List<RecipeVersion> findByRecipeId(long recipeId) {
        if (recipeId <= 0) {
            return List.of();
        }
        return versionMapper.selectList(new LambdaQueryWrapper<RecipeVersionEntity>()
                        .eq(RecipeVersionEntity::getRecipeId, recipeId)
                        .orderByAsc(RecipeVersionEntity::getVersion))
                .stream().map(this::fromRow).toList();
    }

    @Override
    public Optional<RecipeVersion> findLatest(long recipeId) {
        if (recipeId <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(versionMapper.selectOne(
                        new LambdaQueryWrapper<RecipeVersionEntity>()
                                .eq(RecipeVersionEntity::getRecipeId, recipeId)
                                .orderByDesc(RecipeVersionEntity::getVersion)
                                .last("LIMIT 1")))
                .map(this::fromRow);
    }

    private RecipeVersion fromRow(RecipeVersionEntity row) {
        return RecipeVersion.restore(row.getId(), row.getRecipeId(), row.getVersion(),
                row.getWhenJson(), row.getIfJson(), row.getThenJson(),
                Objects.requireNonNull(row.getCreatedTime(), "createdTime").toInstant());
    }
}
