package com.li.lipicturecloud.infrastructure.companion;

import com.li.lipicturecloud.application.companion.CompanionMemoryIndex;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 未配置向量库或向量模型时的空实现：召回始终走字面匹配。 */
public class UnavailableCompanionMemoryIndex implements CompanionMemoryIndex {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public Map<Long, String> fingerprints(long companionId, long subjectId) {
        throw unavailable();
    }

    @Override
    public void upsert(long companionId, long subjectId, List<Entry> entries) {
        throw unavailable();
    }

    @Override
    public void remove(Collection<Long> memoryIds) {
        // Nothing is indexed.
    }

    @Override
    public List<Hit> search(long companionId, long subjectId, String query, int limit) {
        throw unavailable();
    }

    private static IndexUnavailableException unavailable() {
        return new IndexUnavailableException("memory vector index is not configured", null);
    }
}
