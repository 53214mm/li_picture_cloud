package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionSkill;
import com.li.lipicturecloud.domain.companion.CompanionStage;
import com.li.lipicturecloud.domain.companion.CompanionTraits;
import com.li.lipicturecloud.domain.companion.TraitDelta;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 伙伴持久化专用的 JSON 编解码器。
 *
 * <p>领域层使用特质增量、技能 Map 和伙伴对象等强类型数据，数据库则把部分数据保存为 JSON。
 * 本类集中处理两种表示之间的转换，避免 Jackson 细节散落到各个 Repository 中。</p>
 *
 * <p>它只负责数据格式转换，不负责经验、特质或技能的成长计算。</p>
 */
@Component
public class CompanionJsonCodec {

    private static final TypeReference<Map<String, Long>> STRING_LONG_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public CompanionJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 将一次成长的特质增量编码为数据库可保存的 JSON。 */
    public String writeTraitDelta(TraitDelta value) {
        return write(Objects.requireNonNull(value, "trait delta"));
    }

    /** 将数据库中的特质增量 JSON 恢复成领域值对象。 */
    public TraitDelta readTraitDelta(String json) {
        return read(json, TraitDelta.class);
    }

    /** 将技能经验增量编码为以技能名称为键的 JSON。 */
    public String writeSkillDelta(Map<CompanionSkill, Long> value) {
        Objects.requireNonNull(value, "skill delta");
        Map<String, Long> payload = new HashMap<>();
        value.forEach((skill, experience) -> payload.put(skill.name(), experience));
        return write(payload);
    }

    /** 恢复技能经验增量；后续会为旧数据中缺失的新技能补 0。 */
    public Map<CompanionSkill, Long> readSkillDelta(String json) {
        Map<String, Long> payload = read(json, STRING_LONG_MAP);
        Map<CompanionSkill, Long> result = new EnumMap<>(CompanionSkill.class);
        for (Map.Entry<String, Long> entry : payload.entrySet()) {
            CompanionSkill skill = CompanionSkill.valueOf(entry.getKey());
            Long experience = entry.getValue();
            if (experience == null || experience < 0) {
                throw new IllegalStateException("伙伴持久化数据无法解析");
            }
            result.put(skill, experience);
        }
        return Map.copyOf(result);
    }

    /** 保存成长后的伙伴快照；它用于历史展示，不是当前伙伴状态的事实来源。 */
    public String writeSnapshot(Companion companion) {
        Objects.requireNonNull(companion, "companion");
        Map<String, Long> skills = new HashMap<>();
        companion.skillExperience().forEach((skill, experience) -> skills.put(skill.name(), experience));
        return write(new CompanionSnapshotPayload(
                companion.id(), companion.ownerId(), companion.lifeExperience(), companion.level(),
                companion.lifeStage().name(), companion.traits().curiosity(), companion.traits().enthusiasm(),
                companion.traits().playfulness(), companion.traits().empathy(), companion.traits().creativity(),
                skills, companion.balanceVersion(), companion.revision()));
    }

    /** 将历史快照恢复成当时的伙伴状态，并注入领域规则需要的平衡参数。 */
    public Companion readSnapshot(String json, CompanionBalance balance) {
        CompanionSnapshotPayload payload = read(json, CompanionSnapshotPayload.class);
        Map<CompanionSkill, Long> skills = allSkills(payload.skills());
        return Companion.restore(payload.id(), payload.ownerId(), payload.lifeExperience(), payload.level(),
                CompanionStage.valueOf(payload.lifeStage()), new CompanionTraits(
                        payload.curiosity(), payload.enthusiasm(), payload.playfulness(), payload.empathy(),
                        payload.creativity()), skills, payload.balanceVersion(), payload.revision(), balance);
    }

    private Map<CompanionSkill, Long> allSkills(Map<String, Long> storedSkills) {
        Objects.requireNonNull(storedSkills, "skills");
        Map<CompanionSkill, Long> result = new EnumMap<>(CompanionSkill.class);
        for (CompanionSkill skill : CompanionSkill.values()) {
            Long value = storedSkills.get(skill.name());
            if (value == null) {
                value = 0L;
            }
            if (value < 0) {
                throw new IllegalStateException("伙伴持久化数据无法解析");
            }
            result.put(skill, value);
        }
        return result;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("伙伴持久化数据无法解析", exception);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("伙伴持久化数据无法解析", exception);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("伙伴持久化数据无法解析", exception);
        }
    }

    private record CompanionSnapshotPayload(
            Long id,
            long ownerId,
            long lifeExperience,
            int level,
            String lifeStage,
            BigDecimal curiosity,
            BigDecimal enthusiasm,
            BigDecimal playfulness,
            BigDecimal empathy,
            BigDecimal creativity,
            Map<String, Long> skills,
            String balanceVersion,
            long revision) {
    }
}
