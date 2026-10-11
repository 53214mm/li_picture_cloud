package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.li.lipicturecloud.domain.companion.CompanionChatMode;
import com.li.lipicturecloud.domain.companion.CompanionChatPreferenceRepository;
import com.li.lipicturecloud.mapper.CompanionChatPreferenceMapper;
import com.li.lipicturecloud.model.entity.CompanionChatPreferenceEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisCompanionChatPreferenceRepository implements CompanionChatPreferenceRepository {

    private final CompanionChatPreferenceMapper mapper;
    private final Clock clock;

    public MybatisCompanionChatPreferenceRepository(CompanionChatPreferenceMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public Optional<CompanionChatMode> findMode(long subjectId) {
        CompanionChatPreferenceEntity row = mapper.selectOne(
                new LambdaQueryWrapper<CompanionChatPreferenceEntity>()
                        .eq(CompanionChatPreferenceEntity::getSubjectId, subjectId));
        return Optional.ofNullable(row).map(found -> CompanionChatMode.valueOf(found.getChatMode()));
    }

    @Override
    public void saveMode(long subjectId, CompanionChatMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
        if (update(subjectId, mode)) {
            return;
        }
        CompanionChatPreferenceEntity row = new CompanionChatPreferenceEntity();
        row.setSubjectId(subjectId);
        row.setChatMode(mode.name());
        Date now = Date.from(clock.instant());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException raceWonElsewhere) {
            // 并发首写：另一请求已插入同一用户的行，最后一次选择覆盖它。
            update(subjectId, mode);
        }
    }

    private boolean update(long subjectId, CompanionChatMode mode) {
        return mapper.update(null, new LambdaUpdateWrapper<CompanionChatPreferenceEntity>()
                .eq(CompanionChatPreferenceEntity::getSubjectId, subjectId)
                .set(CompanionChatPreferenceEntity::getChatMode, mode.name())
                .set(CompanionChatPreferenceEntity::getUpdateTime, Date.from(clock.instant()))) == 1;
    }
}
