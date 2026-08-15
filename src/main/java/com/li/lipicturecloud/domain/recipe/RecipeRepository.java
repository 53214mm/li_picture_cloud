package com.li.lipicturecloud.domain.recipe;

import java.util.List;
import java.util.Optional;

/**
 * 配方的持久化端口。写入走 revision CAS（revision 恰好 +1）。
 */
public interface RecipeRepository {

    Optional<Recipe> findById(long id);

    List<Recipe> findBySubjectId(long subjectId, int limit);

    List<Recipe> findEnabledBySubjectId(long subjectId);

    Recipe insert(Recipe recipe);

    boolean save(Recipe after, long expectedRevision);
}
