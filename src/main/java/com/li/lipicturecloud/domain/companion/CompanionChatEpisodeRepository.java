package com.li.lipicturecloud.domain.companion;

import java.util.List;

/**
 * 聊天经历的持久化端口。
 */
public interface CompanionChatEpisodeRepository {

    /** 追加一段经历；同一段（同一伙伴、同一最后消息）已整理过时返回 {@code false}。 */
    boolean append(CompanionChatEpisode episode);

    /** 最近的经历（按结束时间倒序），limit 由实现钳制在 [1, 100]。 */
    List<CompanionChatEpisode> findRecent(long companionId, int limit);

    /** 已整理到的最后一条消息 id；还没有经历时为 0。 */
    long lastCoveredMessageId(long companionId);

    /** 删除一个伙伴的全部经历，返回删除条数。 */
    int deleteByCompanion(long companionId);
}
