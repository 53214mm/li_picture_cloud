package com.li.lipicturecloud.infrastructure.persistence.airuntime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.lipicturecloud.domain.airuntime.McpConnection;
import com.li.lipicturecloud.domain.airuntime.McpConnectionRepository;
import com.li.lipicturecloud.mapper.McpConnectionMapper;
import com.li.lipicturecloud.model.entity.McpConnectionEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisMcpConnectionRepository implements McpConnectionRepository {

    private final McpConnectionMapper connectionMapper;
    private final Clock clock;

    public MybatisMcpConnectionRepository(McpConnectionMapper connectionMapper, Clock clock) {
        this.connectionMapper = connectionMapper;
        this.clock = clock;
    }

    @Override
    public Optional<McpConnection> findByCode(String code) {
        Objects.requireNonNull(code, "code");
        return Optional.ofNullable(connectionMapper.selectOne(
                        new LambdaQueryWrapper<McpConnectionEntity>()
                                .eq(McpConnectionEntity::getCode, code)))
                .map(this::fromRow);
    }

    @Override
    public List<McpConnection> findAll() {
        return connectionMapper.selectList(new LambdaQueryWrapper<McpConnectionEntity>()
                        .orderByAsc(McpConnectionEntity::getId))
                .stream().map(this::fromRow).toList();
    }

    @Override
    public McpConnection insert(McpConnection connection) {
        Objects.requireNonNull(connection, "connection");
        if (connection.id() != null) {
            throw new IllegalArgumentException("cannot insert an already persisted mcp connection");
        }
        McpConnectionEntity row = toRow(connection);
        try {
            connectionMapper.insert(row);
        } catch (DuplicateKeyException raceWonElsewhere) {
            // code 唯一键是最终仲裁；并发创建时输的一方读取赢家行。
            return findByCode(connection.code())
                    .orElseThrow(() -> new IllegalStateException("MCP 服务唯一键冲突后无法读取已有行",
                            raceWonElsewhere));
        }
        return connection.withId(Objects.requireNonNull(row.getId(), "assigned mcp connection id"));
    }

    @Override
    public boolean save(McpConnection after, long expectedRevision) {
        Objects.requireNonNull(after, "connection");
        if (after.id() == null) {
            throw new IllegalArgumentException("cannot save an unpersisted mcp connection");
        }
        if (expectedRevision < 0 || after.revision() != Math.addExact(expectedRevision, 1L)) {
            throw new IllegalArgumentException("mcp connection revision must advance by exactly one");
        }
        UpdateWrapper<McpConnectionEntity> update = new UpdateWrapper<>();
        update.eq("id", after.id())
                .eq("revision", expectedRevision)
                .set("enabled", after.enabled())
                .set("revision", after.revision())
                .set("updateTime", Date.from(clock.instant()));
        return connectionMapper.update(null, update) == 1;
    }

    @Override
    public boolean delete(long id, long expectedRevision) {
        if (id <= 0 || expectedRevision < 0) {
            return false;
        }
        return connectionMapper.delete(new LambdaQueryWrapper<McpConnectionEntity>()
                .eq(McpConnectionEntity::getId, id)
                .eq(McpConnectionEntity::getRevision, expectedRevision)) == 1;
    }

    private McpConnection fromRow(McpConnectionEntity row) {
        return McpConnection.restore(row.getId(), row.getCode(), row.getDisplayName(),
                URI.create(row.getEndpointUri()), Boolean.TRUE.equals(row.getEnabled()),
                Objects.requireNonNull(row.getRevision(), "revision"));
    }

    private McpConnectionEntity toRow(McpConnection connection) {
        McpConnectionEntity row = new McpConnectionEntity();
        row.setId(connection.id());
        row.setCode(connection.code());
        row.setDisplayName(connection.displayName());
        row.setEndpointUri(connection.endpointUri().toString());
        row.setEnabled(connection.enabled());
        row.setRevision(connection.revision());
        Date now = Date.from(clock.instant());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }
}
