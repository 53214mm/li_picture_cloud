package com.li.lipicturecloud.infrastructure.persistence.airuntime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.li.lipicturecloud.domain.airuntime.VisionSearchPreferenceRepository;
import com.li.lipicturecloud.mapper.ModelVisionPreferenceMapper;
import com.li.lipicturecloud.model.entity.ModelVisionPreferenceEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Date;

@Repository
public class MybatisVisionSearchPreferenceRepository implements VisionSearchPreferenceRepository {

    private final ModelVisionPreferenceMapper mapper;
    private final Clock clock;

    public MybatisVisionSearchPreferenceRepository(ModelVisionPreferenceMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public boolean forcedSearch(long subjectId) {
        ModelVisionPreferenceEntity row = mapper.selectOne(new LambdaQueryWrapper<ModelVisionPreferenceEntity>()
                .eq(ModelVisionPreferenceEntity::getSubjectId, subjectId));
        return row != null && Boolean.TRUE.equals(row.getForcedSearch());
    }

    @Override
    public void save(long subjectId, boolean forcedSearch) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
        if (update(subjectId, forcedSearch)) {
            return;
        }
        ModelVisionPreferenceEntity row = new ModelVisionPreferenceEntity();
        row.setSubjectId(subjectId);
        row.setForcedSearch(forcedSearch);
        Date now = Date.from(clock.instant());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException raceWonElsewhere) {
            // 并发首写：另一请求已插入同一用户的行，最后一次选择覆盖它。
            update(subjectId, forcedSearch);
        }
    }

    private boolean update(long subjectId, boolean forcedSearch) {
        return mapper.update(null, new LambdaUpdateWrapper<ModelVisionPreferenceEntity>()
                .eq(ModelVisionPreferenceEntity::getSubjectId, subjectId)
                .set(ModelVisionPreferenceEntity::getForcedSearch, forcedSearch)
                .set(ModelVisionPreferenceEntity::getUpdateTime, Date.from(clock.instant()))) == 1;
    }
}
