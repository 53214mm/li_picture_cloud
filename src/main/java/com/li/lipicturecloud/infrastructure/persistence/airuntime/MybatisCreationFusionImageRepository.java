package com.li.lipicturecloud.infrastructure.persistence.airuntime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.lipicturecloud.domain.airuntime.CreationFusionImage;
import com.li.lipicturecloud.domain.airuntime.CreationFusionImageRepository;
import com.li.lipicturecloud.mapper.CreationFusionImageMapper;
import com.li.lipicturecloud.model.entity.CreationFusionImageEntity;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisCreationFusionImageRepository implements CreationFusionImageRepository {

    private final CreationFusionImageMapper fusionImageMapper;

    public MybatisCreationFusionImageRepository(CreationFusionImageMapper fusionImageMapper) {
        this.fusionImageMapper = fusionImageMapper;
    }

    @Override
    public CreationFusionImage insert(CreationFusionImage image) {
        Objects.requireNonNull(image, "image");
        if (image.id() != null) {
            throw new IllegalArgumentException("cannot insert an already persisted fusion image");
        }
        CreationFusionImageEntity row = new CreationFusionImageEntity();
        row.setTaskId(image.taskId());
        row.setMimeType(image.mimeType());
        row.setBytes(image.bytes());
        row.setCreatedTime(Date.from(image.createdTime()));
        fusionImageMapper.insert(row);
        return image.withId(Objects.requireNonNull(row.getId(), "assigned fusion image id"));
    }

    @Override
    public Optional<CreationFusionImage> findByTaskId(long taskId) {
        if (taskId <= 0) {
            return Optional.empty();
        }
        CreationFusionImageEntity row = fusionImageMapper.selectOne(
                new LambdaQueryWrapper<CreationFusionImageEntity>()
                        .eq(CreationFusionImageEntity::getTaskId, taskId));
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new CreationFusionImage(row.getId(), row.getTaskId(), row.getMimeType(),
                row.getBytes(),
                Objects.requireNonNull(row.getCreatedTime(), "createdTime").toInstant()));
    }
}
