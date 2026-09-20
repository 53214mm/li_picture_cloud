package com.li.lipicturecloud.application.recipe;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.domain.recipe.RecipeDefinition;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 配方定义 JSON 编解码器：只接受白名单键值与已知类型，未知字段/类型一律大声失败。
 * 使用独立严格 ObjectMapper（FAIL_ON_UNKNOWN_PROPERTIES），不改动共享 mapper 配置。
 */
@Component
public class RecipeDefinitionCodec {

    private final ObjectMapper strictMapper;

    public RecipeDefinitionCodec(ObjectMapper objectMapper) {
        this.strictMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public RecipeDefinitionJson encode(RecipeDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        try {
            return new RecipeDefinitionJson(
                    strictMapper.writeValueAsString(java.util.Map.of(
                            "type", definition.when().type().name())),
                    strictMapper.writeValueAsString(encodeConditions(definition.conditions())),
                    strictMapper.writeValueAsString(java.util.Map.of(
                            "capability", definition.then().capability().name())));
        } catch (Exception failure) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "配方定义序列化失败");
        }
    }

    public RecipeDefinition decode(String whenJson, String ifJson, String thenJson) {
        try {
            JsonNode when = strictMapper.readTree(whenJson);
            JsonNode conditions = strictMapper.readTree(ifJson);
            JsonNode then = strictMapper.readTree(thenJson);
            return fromNodes(when, conditions, then);
        } catch (BusinessException rejected) {
            throw rejected;
        } catch (Exception malformed) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "配方定义格式不正确");
        }
    }

    /** 从请求体 JSON 树解析（严格模式）；未知键或非法值一律大声失败。 */
    public RecipeDefinition decodeNode(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "配方定义格式不正确");
        }
        requireOnlyFields(body, "when", "conditions", "then");
        return fromNodes(body.path("when"), body.path("conditions"), body.path("then"));
    }

    private com.li.lipicturecloud.domain.recipe.RecipeDefinition fromNodes(JsonNode whenNode,
                                                                           JsonNode conditionsNode,
                                                                           JsonNode thenNode) {
        com.li.lipicturecloud.domain.recipe.RecipeWhen when = parseWhen(whenNode);
        List<com.li.lipicturecloud.domain.recipe.RecipeIfCondition> conditions =
                parseConditions(conditionsNode);
        com.li.lipicturecloud.domain.recipe.RecipeThen then = parseThen(thenNode);
        return new com.li.lipicturecloud.domain.recipe.RecipeDefinition(when, conditions, then);
    }

    private com.li.lipicturecloud.domain.recipe.RecipeWhen parseWhen(JsonNode node) {
        if (node == null || !node.isObject() || !node.path("type").isTextual()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "配方 WHEN 必须是 {type: 已知类型}");
        }
        requireOnlyFields(node, "type");
        try {
            return new com.li.lipicturecloud.domain.recipe.RecipeWhen(
                    com.li.lipicturecloud.domain.recipe.RecipeWhenType.valueOf(
                            node.path("type").asText()));
        } catch (IllegalArgumentException unknown) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未知的配方触发类型");
        }
    }

    private List<com.li.lipicturecloud.domain.recipe.RecipeIfCondition> parseConditions(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "配方 IF 必须是条件数组（可为空）");
        }
        List<com.li.lipicturecloud.domain.recipe.RecipeIfCondition> conditions = new ArrayList<>();
        for (JsonNode item : node) {
            conditions.add(parseCondition(item));
        }
        if (conditions.size() > com.li.lipicturecloud.domain.recipe.RecipeDefinition.MAX_CONDITIONS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "配方 IF 条件最多 5 条");
        }
        return conditions;
    }

    private com.li.lipicturecloud.domain.recipe.RecipeIfCondition parseCondition(JsonNode node) {
        if (node == null || !node.isObject() || !node.path("type").isTextual()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "配方 IF 条件格式不正确");
        }
        try {
            return switch (node.path("type").asText()) {
                case "SOURCE_SPACE_PRIVATE" -> {
                    requireOnlyFields(node, "type");
                    yield new com.li.lipicturecloud.domain.recipe.RecipeIfCondition.SourceSpacePrivate();
                }
                case "SOURCE_CATEGORY" -> {
                    requireOnlyFields(node, "type", "category");
                    yield new com.li.lipicturecloud.domain.recipe.RecipeIfCondition.SourceCategory(
                            node.path("category").asText());
                }
                case "MAX_TRIAL_COST" -> {
                    requireOnlyFields(node, "type", "units");
                    yield new com.li.lipicturecloud.domain.recipe.RecipeIfCondition.MaxTrialCost(
                            node.path("units").asLong());
                }
                default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "未知的配方 IF 条件类型");
            };
        } catch (BusinessException rejected) {
            throw rejected;
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "配方 IF 条件取值不合法");
        }
    }

    private com.li.lipicturecloud.domain.recipe.RecipeThen parseThen(JsonNode node) {
        if (node == null || !node.isObject() || !node.path("capability").isTextual()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "配方 THEN 必须是 {capability: 白名单能力}");
        }
        requireOnlyFields(node, "capability");
        try {
            return new com.li.lipicturecloud.domain.recipe.RecipeThen(
                    com.li.lipicturecloud.domain.airuntime.CreationKind.valueOf(
                            node.path("capability").asText()));
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "配方 THEN 能力不在白名单内");
        }
    }

    /** 未知字段一律拒绝：配方 JSON 只允许白名单键（收紧不扩大语义的语法层保证）。 */
    private static void requireOnlyFields(JsonNode node, String... allowed) {
        java.util.Set<String> allowedSet = java.util.Set.of(allowed);
        var fields = node.fieldNames();
        while (fields.hasNext()) {
            if (!allowedSet.contains(fields.next())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "配方定义包含未知字段");
            }
        }
    }

    private List<java.util.Map<String, Object>> encodeConditions(
            List<com.li.lipicturecloud.domain.recipe.RecipeIfCondition> conditions) {
        return conditions.stream().map(condition -> {
            if (condition instanceof com.li.lipicturecloud.domain.recipe.RecipeIfCondition.SourceSpacePrivate) {
                return java.util.Map.<String, Object>of("type", "SOURCE_SPACE_PRIVATE");
            }
            if (condition instanceof com.li.lipicturecloud.domain.recipe.RecipeIfCondition.SourceCategory category) {
                return java.util.Map.<String, Object>of("type", "SOURCE_CATEGORY",
                        "category", category.category());
            }
            if (condition instanceof com.li.lipicturecloud.domain.recipe.RecipeIfCondition.MaxTrialCost cost) {
                return java.util.Map.<String, Object>of("type", "MAX_TRIAL_COST",
                        "units", cost.units());
            }
            throw new IllegalStateException("unknown recipe condition kind");
        }).toList();
    }

    /** 三段 JSON 的载体。 */
    public record RecipeDefinitionJson(String whenJson, String ifJson, String thenJson) {
    }
}
