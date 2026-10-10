package com.li.lipicturecloud.application.companion;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 记忆的语义检索索引（向量库）。它只是 MySQL 记忆的派生副本：
 * 检索结果必须再与"已授权、已确认"的记忆求交集后才能使用。
 *
 * <p>任何方法失败都抛 {@link IndexUnavailableException}，调用方据此退回字面匹配，聊天不受影响。</p>
 */
public interface CompanionMemoryIndex extends AutoCloseable {

    /** 索引当前是否可用（已配置且最近没有连续失败）。 */
    boolean available();

    /** 已索引记忆 id 到文本指纹的映射，用于发现新增或被纠正的记忆。 */
    Map<Long, String> fingerprints(long companionId, long subjectId);

    /** 写入或覆盖一批记忆的向量。 */
    void upsert(long companionId, long subjectId, List<Entry> entries);

    void remove(Collection<Long> memoryIds);

    /** 按语义相似度返回最相近的记忆，相似度越高越靠前。 */
    List<Hit> search(long companionId, long subjectId, String query, int limit);

    /** Releases connections held by the index; the default index holds none. */
    @Override
    default void close() {
    }

    record Entry(long memoryId, String text, String fingerprint) {
    }

    record Hit(long memoryId, double similarity) {
    }

    class IndexUnavailableException extends RuntimeException {
        public IndexUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
