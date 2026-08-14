package com.li.lipicturecloud.infrastructure.persistence.airuntime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.lipicturecloud.domain.airuntime.CreationCandidate;
import com.li.lipicturecloud.domain.airuntime.CreationCandidateRepository;
import com.li.lipicturecloud.mapper.CreationCandidateMapper;
import com.li.lipicturecloud.model.entity.CreationCandidateEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Repository
public class MybatisCreationCandidateRepository implements CreationCandidateRepository {

    private final CreationCandidateMapper candidateMapper;

    public MybatisCreationCandidateRepository(CreationCandidateMapper candidateMapper) {
        this.candidateMapper = candidateMapper;
    }

    @Override
    public List<CreationCandidate> appendAll(long taskId, List<String> texts, Instant now) {
        Objects.requireNonNull(texts, "texts");
        Objects.requireNonNull(now, "now");
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId must be positive");
        }
        List<CreationCandidate> appended = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            CreationCandidate candidate = new CreationCandidate(null, taskId, i, texts.get(i), now);
            CreationCandidateEntity row = new CreationCandidateEntity();
            row.setTaskId(taskId);
            row.setSeq(i);
            row.setText(candidate.text());
            row.setCreatedTime(Date.from(now));
            candidateMapper.insert(row);
            appended.add(candidate.withId(Objects.requireNonNull(row.getId(),
                    "assigned candidate id")));
        }
        return appended;
    }

    @Override
    public List<CreationCandidate> findByTaskId(long taskId) {
        if (taskId <= 0) {
            return List.of();
        }
        return candidateMapper.selectList(new LambdaQueryWrapper<CreationCandidateEntity>()
                        .eq(CreationCandidateEntity::getTaskId, taskId)
                        .orderByAsc(CreationCandidateEntity::getSeq))
                .stream().map(this::fromRow).toList();
    }

    private CreationCandidate fromRow(CreationCandidateEntity row) {
        return new CreationCandidate(row.getId(), row.getTaskId(), row.getSeq(), row.getText(),
                Objects.requireNonNull(row.getCreatedTime(), "createdTime").toInstant());
    }
}
