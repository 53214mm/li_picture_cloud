package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.lipicturecloud.domain.companion.CompanionChatEpisode;
import com.li.lipicturecloud.domain.companion.CompanionChatEpisodeRepository;
import com.li.lipicturecloud.mapper.CompanionChatEpisodeMapper;
import com.li.lipicturecloud.model.entity.CompanionChatEpisodeEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Repository
public class MybatisCompanionChatEpisodeRepository implements CompanionChatEpisodeRepository {

    private final CompanionChatEpisodeMapper mapper;

    public MybatisCompanionChatEpisodeRepository(CompanionChatEpisodeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean append(CompanionChatEpisode episode) {
        Objects.requireNonNull(episode, "episode");
        if (episode.id() != null) {
            throw new IllegalArgumentException("cannot append an already persisted episode");
        }
        CompanionChatEpisodeEntity row = new CompanionChatEpisodeEntity();
        row.setCompanionId(episode.companionId());
        row.setSubjectId(episode.subjectId());
        row.setFirstMessageId(episode.firstMessageId());
        row.setLastMessageId(episode.lastMessageId());
        row.setStartedTime(Date.from(episode.startedTime()));
        row.setEndedTime(Date.from(episode.endedTime()));
        row.setSummary(episode.summary());
        row.setModelProvider(episode.modelProvider());
        row.setModelCode(episode.modelCode());
        row.setCreateTime(Date.from(episode.createdTime()));
        try {
            return mapper.insert(row) == 1;
        } catch (DuplicateKeyException alreadyConsolidated) {
            // 并发的另一轮已经整理过这一段。
            return false;
        }
    }

    @Override
    public List<CompanionChatEpisode> findRecent(long companionId, int limit) {
        return mapper.selectList(new LambdaQueryWrapper<CompanionChatEpisodeEntity>()
                        .eq(CompanionChatEpisodeEntity::getCompanionId, companionId)
                        .orderByDesc(CompanionChatEpisodeEntity::getEndedTime)
                        .orderByDesc(CompanionChatEpisodeEntity::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100))))
                .stream().map(MybatisCompanionChatEpisodeRepository::fromRow).toList();
    }

    @Override
    public long lastCoveredMessageId(long companionId) {
        CompanionChatEpisodeEntity latest = mapper.selectOne(new LambdaQueryWrapper<CompanionChatEpisodeEntity>()
                .select(CompanionChatEpisodeEntity::getLastMessageId)
                .eq(CompanionChatEpisodeEntity::getCompanionId, companionId)
                .orderByDesc(CompanionChatEpisodeEntity::getLastMessageId)
                .last("LIMIT 1"));
        return latest == null || latest.getLastMessageId() == null ? 0L : latest.getLastMessageId();
    }

    @Override
    public int deleteByCompanion(long companionId) {
        return mapper.delete(new LambdaQueryWrapper<CompanionChatEpisodeEntity>()
                .eq(CompanionChatEpisodeEntity::getCompanionId, companionId));
    }

    private static CompanionChatEpisode fromRow(CompanionChatEpisodeEntity row) {
        return new CompanionChatEpisode(row.getId(), row.getCompanionId(), row.getSubjectId(),
                row.getFirstMessageId(), row.getLastMessageId(), row.getStartedTime().toInstant(),
                row.getEndedTime().toInstant(), row.getSummary() == null ? "" : row.getSummary(),
                row.getModelProvider(), row.getModelCode(), row.getCreateTime().toInstant());
    }
}
