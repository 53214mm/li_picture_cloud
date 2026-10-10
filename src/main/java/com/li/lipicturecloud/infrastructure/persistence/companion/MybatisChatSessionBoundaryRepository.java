package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.li.lipicturecloud.domain.companion.ChatSessionBoundaryRepository;
import com.li.lipicturecloud.mapper.CompanionChatBoundaryMapper;
import com.li.lipicturecloud.model.entity.CompanionChatBoundaryEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Repository
public class MybatisChatSessionBoundaryRepository implements ChatSessionBoundaryRepository {

    private final CompanionChatBoundaryMapper mapper;

    public MybatisChatSessionBoundaryRepository(CompanionChatBoundaryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Instant> lastLogin(long subjectId) {
        CompanionChatBoundaryEntity row = mapper.selectOne(new LambdaQueryWrapper<CompanionChatBoundaryEntity>()
                .eq(CompanionChatBoundaryEntity::getSubjectId, subjectId));
        return Optional.ofNullable(row).map(CompanionChatBoundaryEntity::getLoginTime).map(Date::toInstant);
    }

    @Override
    public void recordLogin(long subjectId, Instant loginTime) {
        Date login = Date.from(loginTime);
        if (update(subjectId, login)) {
            return;
        }
        CompanionChatBoundaryEntity row = new CompanionChatBoundaryEntity();
        row.setSubjectId(subjectId);
        row.setLoginTime(login);
        row.setUpdateTime(login);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException concurrentLogin) {
            update(subjectId, login);
        }
    }

    private boolean update(long subjectId, Date login) {
        return mapper.update(null, new LambdaUpdateWrapper<CompanionChatBoundaryEntity>()
                .eq(CompanionChatBoundaryEntity::getSubjectId, subjectId)
                .set(CompanionChatBoundaryEntity::getLoginTime, login)
                .set(CompanionChatBoundaryEntity::getUpdateTime, login)) > 0;
    }
}
