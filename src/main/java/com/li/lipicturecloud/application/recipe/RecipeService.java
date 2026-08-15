package com.li.lipicturecloud.application.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import com.li.lipicturecloud.application.recipe.view.RecipeDetailView;
import com.li.lipicturecloud.application.recipe.view.RecipeTemplateView;
import com.li.lipicturecloud.application.recipe.view.RecipeVersionView;
import com.li.lipicturecloud.application.recipe.view.RecipeView;
import com.li.lipicturecloud.domain.recipe.Recipe;
import com.li.lipicturecloud.domain.recipe.RecipeDefinition;
import com.li.lipicturecloud.domain.recipe.RecipeExecutionRepository;
import com.li.lipicturecloud.domain.recipe.RecipeRepository;
import com.li.lipicturecloud.domain.recipe.RecipeVersion;
import com.li.lipicturecloud.domain.recipe.RecipeVersionRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 玩法配方应用服务：创建/模板起点/版本发布（append-only）/启用/停用/删除与详情回放。
 * 定义一律经 {@link RecipeDefinitionCodec} 严格校验（白名单键值、收紧语义），
 * 版本号 = 最新版本 + 1，(recipeId, version) 唯一键兜底并发。
 */
@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository versionRepository;
    private final RecipeExecutionRepository executionRepository;
    private final RecipeDefinitionCodec codec;
    private final Clock clock;

    public RecipeService(RecipeRepository recipeRepository,
                         RecipeVersionRepository versionRepository,
                         RecipeExecutionRepository executionRepository,
                         RecipeDefinitionCodec codec,
                         Clock clock) {
        this.recipeRepository = recipeRepository;
        this.versionRepository = versionRepository;
        this.executionRepository = executionRepository;
        this.codec = codec;
        this.clock = clock;
    }

    public RecipeView create(AuthorizationSubject subject, String name) {
        return toView(insertRecipe(subject, name), versionRepository);
    }

    public RecipeDetailView createFromTemplate(AuthorizationSubject subject, String templateCode,
                                               String name) {
        Recipe recipe = insertRecipe(subject, name);
        RecipeDefinition definition = OfficialRecipeTemplates.definition(
                        Objects.requireNonNull(templateCode, "templateCode"))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAMS_ERROR, "未知的官方模板"));
        appendDefinition(recipe, definition);
        return detail(subject, recipe.id());
    }

    public RecipeDetailView publishDefinition(AuthorizationSubject subject, long recipeId,
                                              JsonNode body) {
        Recipe recipe = requireOwned(subject, recipeId);
        RecipeDefinition definition = codec.decodeNode(body);
        appendDefinition(recipe, definition);
        return detail(subject, recipeId);
    }

    public RecipeView enable(AuthorizationSubject subject, long recipeId) {
        Recipe recipe = requireOwned(subject, recipeId);
        try {
            return toView(transition(recipe, recipe.enable(clock.instant())), versionRepository);
        } catch (IllegalStateException wrongState) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "配方状态已变化，请刷新后重试");
        }
    }

    public RecipeView disable(AuthorizationSubject subject, long recipeId) {
        Recipe recipe = requireOwned(subject, recipeId);
        try {
            return toView(transition(recipe, recipe.disable(clock.instant())), versionRepository);
        } catch (IllegalStateException wrongState) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "配方状态已变化，请刷新后重试");
        }
    }

    @Transactional
    public void delete(AuthorizationSubject subject, long recipeId) {
        Recipe recipe = requireOwned(subject, recipeId);
        executionRepository.deleteByRecipeId(recipe.id());
        versionRepository.deleteByRecipeId(recipe.id());
        if (!recipeRepository.deleteById(recipe.id())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "配方删除失败，请重试");
        }
    }

    public List<RecipeView> list(AuthorizationSubject subject, int limit) {
        Objects.requireNonNull(subject, "subject");
        return recipeRepository.findBySubjectId(subject.userId(), limit).stream()
                .map(recipe -> toView(recipe, versionRepository))
                .toList();
    }

    public RecipeDetailView detail(AuthorizationSubject subject, long recipeId) {
        Recipe recipe = requireOwned(subject, recipeId);
        return buildDetail(recipe);
    }

    public List<RecipeTemplateView> templates() {
        return OfficialRecipeTemplates.allCodes().stream()
                .map(code -> {
                    RecipeDefinition definition = OfficialRecipeTemplates.definition(code)
                            .orElseThrow();
                    RecipeDefinitionCodec.RecipeDefinitionJson json = codec.encode(definition);
                    return new RecipeTemplateView(code,
                            OfficialRecipeTemplates.name(code).orElse(code),
                            OfficialRecipeTemplates.description(code).orElse(""),
                            json.whenJson(), json.ifJson(), json.thenJson());
                })
                .toList();
    }

    private Recipe insertRecipe(AuthorizationSubject subject, String name) {
        Objects.requireNonNull(subject, "subject");
        requireRegularUser(subject);
        return recipeRepository.insert(Recipe.create(subject.userId(), requireName(name),
                clock.instant()));
    }

    private void appendDefinition(Recipe recipe, RecipeDefinition definition) {
        int nextVersion = versionRepository.findLatest(recipe.id())
                .map(latest -> latest.version() + 1)
                .orElse(1);
        RecipeDefinitionCodec.RecipeDefinitionJson json = codec.encode(definition);
        versionRepository.append(RecipeVersion.create(recipe.id(), nextVersion,
                json.whenJson(), json.ifJson(), json.thenJson(), clock.instant()));
    }

    private RecipeDetailView buildDetail(Recipe recipe) {
        List<RecipeVersion> versions = versionRepository.findByRecipeId(recipe.id());
        List<RecipeVersionView> versionViews = versions.stream()
                .map(version -> new RecipeVersionView(version.id(), version.version(),
                        version.whenJson(), version.ifJson(), version.thenJson(),
                        version.createdTime()))
                .toList();
        RecipeVersionView latest = versionViews.stream()
                .max(java.util.Comparator.comparingInt(RecipeVersionView::version))
                .orElse(null);
        return new RecipeDetailView(toView(recipe, versionRepository), versionViews, latest);
    }

    private Recipe requireOwned(AuthorizationSubject subject, long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "配方不存在"));
        if (recipe.subjectId() != subject.userId()) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权操作该配方");
        }
        return recipe;
    }

    private Recipe transition(Recipe current, Recipe after) {
        if (!recipeRepository.save(after, current.revision())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "配方发生并发冲突，请重试");
        }
        return after;
    }

    private static RecipeView toView(Recipe recipe, RecipeVersionRepository versionRepository) {
        return new RecipeView(recipe.id(), recipe.subjectId(), recipe.name(),
                recipe.status().name(), recipe.revision(),
                versionRepository.findLatest(recipe.id()).map(RecipeVersion::version).orElse(null),
                recipe.createdTime(), recipe.updatedTime());
    }

    private static String requireName(String name) {
        String normalized = Objects.requireNonNull(name, "name").strip();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "配方名称需为 1-64 个字符");
        }
        return normalized;
    }

    private static void requireRegularUser(AuthorizationSubject subject) {
        if (subject.platformAdmin()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "平台管理员不参与配方工坊");
        }
    }
}
