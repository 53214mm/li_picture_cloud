package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.lipicturecloud.domain.companion.CompanionChatMessage;
import com.li.lipicturecloud.domain.companion.CompanionChatMessageRepository;
import com.li.lipicturecloud.domain.companion.CompanionChatRole;
import com.li.lipicturecloud.mapper.CompanionChatMessageMapper;
import com.li.lipicturecloud.model.entity.CompanionChatMessageEntity;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisCompanionChatMessageRepository implements CompanionChatMessageRepository {

    private final CompanionChatMessageMapper messageMapper;

    public MybatisCompanionChatMessageRepository(CompanionChatMessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @Override
    public CompanionChatMessage append(CompanionChatMessage message) {
        Objects.requireNonNull(message, "message");
        if (message.id() != null) {
            throw new IllegalArgumentException("cannot append an already persisted message");
        }
        CompanionChatMessageEntity row = new CompanionChatMessageEntity();
        row.setCompanionId(message.companionId());
        row.setSubjectId(message.subjectId());
        row.setRole(message.role().name());
        row.setContent(message.content());
        row.setModelProvider(message.modelProvider());
        row.setModelCode(message.modelCode());
        row.setCreateTime(Date.from(message.createdTime()));
        messageMapper.insert(row);
        return message.withId(Objects.requireNonNull(row.getId(), "assigned message id"));
    }

    @Override
    public Optional<CompanionChatMessage> findById(long id) {
        if (id <= 0) {
            return Optional.empty();
        }
        CompanionChatMessageEntity row = messageMapper.selectById(id);
        return Optional.ofNullable(row).map(this::fromRow);
    }

    @Override
    public List<CompanionChatMessage> findRecent(long companionId, int limit) {
        return messageMapper.selectList(new LambdaQueryWrapper<CompanionChatMessageEntity>()
                        .eq(CompanionChatMessageEntity::getCompanionId, companionId)
                        .orderByDesc(CompanionChatMessageEntity::getCreateTime)
                        .orderByDesc(CompanionChatMessageEntity::getId)
                        .last("LIMIT " + boundedLimit(limit)))
                .stream().map(this::fromRow).toList();
    }

    private CompanionChatMessage fromRow(CompanionChatMessageEntity row) {
        return new CompanionChatMessage(row.getId(), row.getCompanionId(), row.getSubjectId(),
                CompanionChatRole.valueOf(row.getRole()), row.getContent(),
                row.getModelProvider(), row.getModelCode(),
                Objects.requireNonNull(row.getCreateTime(), "createTime").toInstant());
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
