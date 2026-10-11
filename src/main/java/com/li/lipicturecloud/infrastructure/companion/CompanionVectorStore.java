package com.li.lipicturecloud.infrastructure.companion;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 向量库连接池 + 向量模型，图片记忆和聊天经历的索引共用。未配置时 {@link #enabled()} 为 false。
 */
public final class CompanionVectorStore implements AutoCloseable {

    private final JdbcTemplate jdbc;
    private final DashScopeTextEmbedder embedder;
    private final AutoCloseable pool;

    private CompanionVectorStore(JdbcTemplate jdbc, DashScopeTextEmbedder embedder, AutoCloseable pool) {
        this.jdbc = jdbc;
        this.embedder = embedder;
        this.pool = pool;
    }

    public static CompanionVectorStore disabled() {
        return new CompanionVectorStore(null, null, null);
    }

    /** @param pool 只给向量库用的连接池，应用关闭时由本对象关闭 */
    public static CompanionVectorStore of(JdbcTemplate jdbc, DashScopeTextEmbedder embedder, AutoCloseable pool) {
        return new CompanionVectorStore(java.util.Objects.requireNonNull(jdbc, "jdbc"),
                java.util.Objects.requireNonNull(embedder, "embedder"), pool);
    }

    public boolean enabled() {
        return jdbc != null;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public DashScopeTextEmbedder embedder() {
        return embedder;
    }

    @Override
    public void close() {
        if (pool != null) {
            try {
                pool.close();
            } catch (Exception ignored) {
                // Shutdown only.
            }
        }
    }
}
