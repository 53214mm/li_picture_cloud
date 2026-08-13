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

@Component
public class CompanionJsonCodec {

    private static final TypeReference<Map<String, Long>> STRING_LONG_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public CompanionJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String writeTraitDelta(TraitDelta value) {
        return write(Objects.requireNonNull(value, "trait delta"));
    }

    public TraitDelta readTraitDelta(String json) {
        return read(json, TraitDelta.class);
    }

    public String writeSkillDelta(Map<CompanionSkill, Long> value) {
        Objects.requireNonNull(value, "skill delta");
        Map<String, Long> payload = new HashMap<>();
        value.forEach((skill, experience) -> payload.put(skill.name(), experience));
        return write(payload);
    }

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
