package com.li.lipicturecloud.infrastructure.persistence.airuntime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.lipicturecloud.domain.airuntime.ModelCapabilityProfile;
import com.li.lipicturecloud.domain.airuntime.ModelCapabilityProfileRepository;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.mapper.ModelCapabilityProfileMapper;
import com.li.lipicturecloud.model.entity.ModelCapabilityProfileEntity;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisModelCapabilityProfileRepository implements ModelCapabilityProfileRepository {

    private final ModelCapabilityProfileMapper profileMapper;

    public MybatisModelCapabilityProfileRepository(ModelCapabilityProfileMapper profileMapper) {
        this.profileMapper = profileMapper;
    }

    @Override
    public ModelCapabilityProfile append(ModelCapabilityProfile profile) {
        Objects.requireNonNull(profile, "profile");
        if (profile.id() != null) {
            throw new IllegalArgumentException("cannot append an already persisted capability profile");
        }
        ModelCapabilityProfileEntity row = new ModelCapabilityProfileEntity();
        row.setConnectionId(profile.connectionId());
        row.setSubjectId(profile.subjectId());
        row.setProvider(profile.provider().name());
        row.setModelCode(profile.modelCode());
        row.setText(profile.text());
        row.setVision(profile.vision());
        row.setToolCall(profile.toolCall());
        row.setStructuredOutput(profile.structuredOutput());
        row.setReasoning(profile.reasoning());
        row.setEmbedding(profile.embedding());
        row.setImageGeneration(profile.imageGeneration());
        row.setMaxContextTokens(profile.maxContextTokens());
        row.setSyncAsync(profile.syncAsync());
        row.setCostHint(profile.costHint());
        row.setCreatedTime(Date.from(profile.createdTime()));
        profileMapper.insert(row);
        return profile.withId(Objects.requireNonNull(row.getId(), "assigned capability profile id"));
    }

    @Override
    public Optional<ModelCapabilityProfile> findLatestByConnectionId(long connectionId) {
        if (connectionId <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(profileMapper.selectOne(
                        new LambdaQueryWrapper<ModelCapabilityProfileEntity>()
                                .eq(ModelCapabilityProfileEntity::getConnectionId, connectionId)
                                .orderByDesc(ModelCapabilityProfileEntity::getCreatedTime)
                                .orderByDesc(ModelCapabilityProfileEntity::getId)
                                .last("LIMIT 1")))
                .map(this::fromRow);
    }

    private ModelCapabilityProfile fromRow(ModelCapabilityProfileEntity row) {
        return new ModelCapabilityProfile(row.getId(), row.getConnectionId(), row.getSubjectId(),
                ModelProvider.valueOf(row.getProvider()), row.getModelCode(),
                Boolean.TRUE.equals(row.getText()), Boolean.TRUE.equals(row.getVision()),
                Boolean.TRUE.equals(row.getToolCall()), Boolean.TRUE.equals(row.getStructuredOutput()),
                Boolean.TRUE.equals(row.getReasoning()), Boolean.TRUE.equals(row.getEmbedding()),
                Boolean.TRUE.equals(row.getImageGeneration()), row.getMaxContextTokens(),
                row.getSyncAsync(), row.getCostHint(),
                Objects.requireNonNull(row.getCreatedTime(), "createdTime").toInstant());
    }
}
