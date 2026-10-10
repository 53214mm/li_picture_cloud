package com.li.lipicturecloud.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.companion.CompanionMemoryIndex;
import com.li.lipicturecloud.infrastructure.companion.CompanionVectorStore;
import com.li.lipicturecloud.infrastructure.companion.DashScopeTextEmbedder;
import com.li.lipicturecloud.infrastructure.companion.PgVectorCompanionMemoryIndex;
import com.li.lipicturecloud.infrastructure.companion.UnavailableCompanionMemoryIndex;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

/**
 * 绫页记忆的向量检索：pgvector（{@code pgvector.datasource.*}）+ 百炼向量模型。
 *
 * <p>两者任一未配置时使用空实现，召回自动退回字面匹配。连接池只给索引自己用，不注册成
 * Spring {@code DataSource} Bean，避免影响主库 MySQL 的自动配置；向量库启动时连不上也不阻止应用启动。
 * 图片记忆与聊天经历共用连接池和向量模型，各用一张表。</p>
 */
@Configuration
public class CompanionMemoryVectorConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CompanionMemoryVectorConfiguration.class);

    @Bean(destroyMethod = "close")
    public CompanionVectorStore companionVectorStore(
            ObjectMapper objectMapper,
            @Value("${app.companion.memory-vector.enabled:true}") boolean enabled,
            @Value("${pgvector.datasource.url:}") String url,
            @Value("${pgvector.datasource.username:postgres}") String username,
            @Value("${pgvector.datasource.password:}") String password,
            @Value("${app.companion.memory-vector.embedding-endpoint:https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings}") String endpoint,
            @Value("${app.companion.memory-vector.embedding-model:qwen3.7-text-embedding}") String model,
            @Value("${app.companion.memory-vector.embedding-api-key:${spring.ai.dashscope.api-key:}}") String apiKey,
            @Value("${app.companion.memory-vector.dimensions:1024}") int dimensions,
            @Value("${app.companion.memory-vector.timeout:5s}") Duration timeout) {
        if (!enabled || url.isBlank() || apiKey.isBlank()) {
            log.info("companion_memory_vector_disabled reason={}",
                    !enabled ? "switched-off" : url.isBlank() ? "no-pgvector-url" : "no-embedding-key");
            return CompanionVectorStore.disabled();
        }
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("companion-memory-vector");
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(3);
        dataSource.setConnectionTimeout(3_000);
        // 向量库暂时连不上时照常启动；第一次使用失败后由索引进入冷却并退回字面匹配。
        dataSource.setInitializationFailTimeout(-1);
        DashScopeTextEmbedder embedder = DashScopeTextEmbedder.production(objectMapper, URI.create(endpoint),
                model, apiKey, dimensions, timeout);
        return CompanionVectorStore.of(new JdbcTemplate(dataSource), embedder, dataSource);
    }

    /** 图片记忆的向量索引。 */
    @Bean
    public CompanionMemoryIndex companionMemoryIndex(CompanionVectorStore store, Clock clock) {
        return index(store, clock, PgVectorCompanionMemoryIndex.MEMORY_TABLE);
    }

    /** 聊天经历的向量索引。 */
    @Bean
    public CompanionMemoryIndex companionEpisodeIndex(CompanionVectorStore store, Clock clock) {
        return index(store, clock, PgVectorCompanionMemoryIndex.EPISODE_TABLE);
    }

    private static CompanionMemoryIndex index(CompanionVectorStore store, Clock clock, String table) {
        return store.enabled()
                ? new PgVectorCompanionMemoryIndex(store.jdbc(), store.embedder(), clock, table)
                : new UnavailableCompanionMemoryIndex();
    }
}
