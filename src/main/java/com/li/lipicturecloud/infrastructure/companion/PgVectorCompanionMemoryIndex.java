package com.li.lipicturecloud.infrastructure.companion;

import com.li.lipicturecloud.application.companion.CompanionMemoryIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 记忆向量索引：PostgreSQL + pgvector，余弦距离 + HNSW。
 *
 * <p>表在首次使用时创建（不属于 MySQL 的 Liquibase 迁移）。连续失败后进入冷却期，期间直接
 * 报不可用，避免每轮聊天都卡在连不上的向量库上。</p>
 */
public class PgVectorCompanionMemoryIndex implements CompanionMemoryIndex {

    private static final Logger log = LoggerFactory.getLogger(PgVectorCompanionMemoryIndex.class);
    private static final Duration COOL_DOWN = Duration.ofMinutes(1);

    private final JdbcTemplate jdbc;
    private final DashScopeTextEmbedder embedder;
    private final Clock clock;
    private volatile boolean schemaReady;
    private volatile Instant unavailableUntil = Instant.MIN;
    private final AutoCloseable resource;

    public PgVectorCompanionMemoryIndex(JdbcTemplate jdbc, DashScopeTextEmbedder embedder, Clock clock) {
        this(jdbc, embedder, clock, null);
    }

    /** @param resource the connection pool owned by this index, closed on shutdown */
    public PgVectorCompanionMemoryIndex(JdbcTemplate jdbc, DashScopeTextEmbedder embedder, Clock clock,
                                        AutoCloseable resource) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.embedder = Objects.requireNonNull(embedder, "embedder");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.resource = resource;
    }

    @Override
    public void close() {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception ignored) {
                // Shutdown only.
            }
        }
    }

    @Override
    public boolean available() {
        return clock.instant().isAfter(unavailableUntil);
    }

    @Override
    public Map<Long, String> fingerprints(long companionId, long subjectId) {
        return guarded(() -> {
            Map<Long, String> fingerprints = new LinkedHashMap<>();
            jdbc.query("SELECT memory_id, fingerprint FROM companion_memory_vector WHERE companion_id = ? AND subject_id = ?",
                    row -> { fingerprints.put(row.getLong(1), row.getString(2)); }, companionId, subjectId);
            return fingerprints;
        });
    }

    @Override
    public void upsert(long companionId, long subjectId, List<Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        guarded(() -> {
            List<float[]> vectors = embedder.embedAll(entries.stream().map(Entry::text).toList());
            List<Object[]> rows = new ArrayList<>(entries.size());
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                rows.add(new Object[]{entry.memoryId(), companionId, subjectId, entry.fingerprint(),
                        literal(vectors.get(i))});
            }
            jdbc.batchUpdate("""
                    INSERT INTO companion_memory_vector (memory_id, companion_id, subject_id, fingerprint, embedding, updated_at)
                    VALUES (?, ?, ?, ?, CAST(? AS vector), now())
                    ON CONFLICT (memory_id) DO UPDATE SET fingerprint = EXCLUDED.fingerprint,
                        embedding = EXCLUDED.embedding, updated_at = now()
                    """, rows);
            return null;
        });
    }

    @Override
    public void remove(Collection<Long> memoryIds) {
        if (memoryIds.isEmpty()) {
            return;
        }
        guarded(() -> {
            jdbc.batchUpdate("DELETE FROM companion_memory_vector WHERE memory_id = ?",
                    memoryIds.stream().map(id -> new Object[]{id}).toList());
            return null;
        });
    }

    @Override
    public List<Hit> search(long companionId, long subjectId, String query, int limit) {
        return guarded(() -> {
            String vector = literal(embedder.embed(query));
            return jdbc.query("""
                    SELECT memory_id, 1 - (embedding <=> CAST(? AS vector)) AS similarity
                    FROM companion_memory_vector
                    WHERE companion_id = ? AND subject_id = ?
                    ORDER BY embedding <=> CAST(? AS vector)
                    LIMIT ?
                    """, (row, index) -> new Hit(row.getLong(1), row.getDouble(2)),
                    vector, companionId, subjectId, vector, Math.max(1, Math.min(limit, 50)));
        });
    }

    private <T> T guarded(Supplier<T> action) {
        if (!available()) {
            throw new IndexUnavailableException("memory vector index is cooling down", null);
        }
        try {
            ensureSchema();
            return action.get();
        } catch (RuntimeException failure) {
            unavailableUntil = clock.instant().plus(COOL_DOWN);
            // 只记异常类型：不记录记忆文本、向量或连接串。
            log.warn("companion_memory_vector_unavailable exceptionType={}", failure.getClass().getName());
            throw new IndexUnavailableException("memory vector index failed", failure);
        }
    }

    private void ensureSchema() {
        if (schemaReady) {
            return;
        }
        synchronized (this) {
            if (schemaReady) {
                return;
            }
            jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
            jdbc.execute("CREATE TABLE IF NOT EXISTS companion_memory_vector ("
                    + "memory_id BIGINT PRIMARY KEY, companion_id BIGINT NOT NULL, subject_id BIGINT NOT NULL, "
                    + "fingerprint VARCHAR(64) NOT NULL, embedding vector(" + embedder.dimensions() + ") NOT NULL, "
                    + "updated_at TIMESTAMPTZ NOT NULL DEFAULT now())");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_companion_memory_vector_owner "
                    + "ON companion_memory_vector (companion_id, subject_id)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_companion_memory_vector_hnsw "
                    + "ON companion_memory_vector USING hnsw (embedding vector_cosine_ops)");
            schemaReady = true;
        }
    }

    static String literal(float[] vector) {
        StringBuilder text = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(vector[i]);
        }
        return text.append(']').toString();
    }
}
