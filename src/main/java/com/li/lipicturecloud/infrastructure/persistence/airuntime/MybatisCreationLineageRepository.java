package com.li.lipicturecloud.infrastructure.persistence.airuntime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.lipicturecloud.domain.airuntime.CreationLineage;
import com.li.lipicturecloud.domain.airuntime.CreationLineageRepository;
import com.li.lipicturecloud.mapper.CreationLineageMapper;
import com.li.lipicturecloud.model.entity.CreationLineageEntity;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Repository
public class MybatisCreationLineageRepository implements CreationLineageRepository {

    private final CreationLineageMapper lineageMapper;

    public MybatisCreationLineageRepository(CreationLineageMapper lineageMapper) {
        this.lineageMapper = lineageMapper;
    }

    @Override
    public CreationLineage append(CreationLineage lineage) {
        Objects.requireNonNull(lineage, "lineage");
        if (lineage.id() != null) {
            throw new IllegalArgumentException("cannot append an already persisted lineage row");
        }
        CreationLineageEntity row = new CreationLineageEntity();
        row.setTaskId(lineage.taskId());
        row.setSourcePictureId(lineage.sourcePictureId());
        row.setResultPictureId(lineage.resultPictureId());
        row.setCapabilityId(lineage.capabilityId());
        row.setModelCode(lineage.modelCode());
        row.setPromptTemplateVersion(lineage.promptTemplateVersion());
        row.setCostSource(lineage.costSource());
        row.setCreatedTime(Date.from(lineage.createdTime()));
        lineageMapper.insert(row);
        return lineage.withId(Objects.requireNonNull(row.getId(), "assigned lineage id"));
    }

    @Override
    public List<CreationLineage> findByTaskId(long taskId) {
        if (taskId <= 0) {
            return List.of();
        }
        return lineageMapper.selectList(new LambdaQueryWrapper<CreationLineageEntity>()
                        .eq(CreationLineageEntity::getTaskId, taskId)
                        .orderByAsc(CreationLineageEntity::getId))
                .stream().map(this::fromRow).toList();
    }

    private CreationLineage fromRow(CreationLineageEntity row) {
        return new CreationLineage(row.getId(), row.getTaskId(), row.getSourcePictureId(),
                row.getResultPictureId(), row.getCapabilityId(), row.getModelCode(),
                row.getPromptTemplateVersion(), row.getCostSource(),
                Objects.requireNonNull(row.getCreatedTime(), "createdTime").toInstant());
    }
}
