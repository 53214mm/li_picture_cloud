package com.li.lipicturecloud.domain.recipe;

import java.util.List;
import java.util.Optional;

/**
 * 配方版本的追加式持久化端口。(recipeId, version) 唯一；只增不改。
 */
public interface RecipeVersionRepository {

    RecipeVersion append(RecipeVersion version);

    List<RecipeVersion> findByRecipeId(long recipeId);

    Optional<RecipeVersion> findLatest(long recipeId);
}
