package com.li.lipicturecloud.domain.companion;

import java.util.List;
import java.util.Optional;

/**
 * 伙伴对话消息的追加式持久化端口。
 */
public interface CompanionChatMessageRepository {

    CompanionChatMessage append(CompanionChatMessage message);

    Optional<CompanionChatMessage> findById(long id);

    /** 最近消息（按时间倒序），limit 由实现钳制在 [1, 100]。 */
    List<CompanionChatMessage> findRecent(long companionId, int limit);

    /** 删除一个伙伴的全部对话消息，返回删除条数。不影响记忆、额度或用量记录。 */
    int deleteByCompanion(long companionId);
}
