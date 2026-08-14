package com.li.lipicturecloud.domain.airuntime;

import java.util.List;

/**
 * 创作候选的追加式持久化端口。
 */
public interface CreationCandidateRepository {

    List<CreationCandidate> appendAll(long taskId, List<String> texts, java.time.Instant now);

    List<CreationCandidate> findByTaskId(long taskId);
}
