package com.li.lipicturecloud.domain.companion;

import java.util.Optional;

/**
 * 用户选择的伙伴聊天模式。没有记录表示用户从未选择过。
 */
public interface CompanionChatPreferenceRepository {

    Optional<CompanionChatMode> findMode(long subjectId);

    /** 写入（不存在则创建）用户的聊天模式。 */
    void saveMode(long subjectId, CompanionChatMode mode);
}
