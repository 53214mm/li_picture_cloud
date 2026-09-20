package com.li.lipicturecloud.infrastructure.persistence.airuntime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.lipicturecloud.domain.airuntime.McpToolWhitelist;
import com.li.lipicturecloud.domain.airuntime.McpToolWhitelistRepository;
import com.li.lipicturecloud.mapper.McpToolWhitelistMapper;
import com.li.lipicturecloud.model.entity.McpToolWhitelistEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisMcpToolWhitelistRepository implements McpToolWhitelistRepository {

    private final McpToolWhitelistMapper whitelistMapper;
    private final Clock clock;

    public MybatisMcpToolWhitelistRepository(McpToolWhitelistMapper whitelistMapper, Clock clock) {
        this.whitelistMapper = whitelistMapper;
        this.clock = clock;
    }

    @Override
    public List<McpToolWhitelist> findByConnectionId(long connectionId) {
        return whitelistMapper.selectList(new LambdaQueryWrapper<McpToolWhitelistEntity>()
                        .eq(McpToolWhitelistEntity::getConnectionId, connectionId)
                        .orderByAsc(McpToolWhitelistEntity::getId))
                .stream().map(this::fromRow).toList();
    }

    @Override
    public Optional<McpToolWhitelist> findByConnectionAndTool(long connectionId, String toolName) {
        Objects.requireNonNull(toolName, "toolName");
        return Optional.ofNullable(whitelistMapper.selectOne(
                        new LambdaQueryWrapper<McpToolWhitelistEntity>()
                                .eq(McpToolWhitelistEntity::getConnectionId, connectionId)
                                .eq(McpToolWhitelistEntity::getToolName, toolName)))
                .map(this::fromRow);
    }

    @Override
    public McpToolWhitelist insert(McpToolWhitelist entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.id() != null) {
            throw new IllegalArgumentException("cannot insert an already persisted whitelist entry");
        }
        McpToolWhitelistEntity row = toRow(entry);
        try {
            whitelistMapper.insert(row);
        } catch (DuplicateKeyException raceWonElsewhere) {
            // (connectionId, toolName) 唯一键是最终仲裁；并发创建时输的一方读取赢家行。
            return findByConnectionAndTool(entry.connectionId(), entry.toolName())
                    .orElseThrow(() -> new IllegalStateException("白名单唯一键冲突后无法读取已有行",
                            raceWonElsewhere));
        }
        return entry.withId(Objects.requireNonNull(row.getId(), "assigned whitelist entry id"));
    }

    @Override
    public boolean save(McpToolWhitelist after, long expectedRevision) {
        Objects.requireNonNull(after, "entry");
        if (after.id() == null) {
            throw new IllegalArgumentException("cannot save an unpersisted whitelist entry");
        }
        if (expectedRevision < 0 || after.revision() != Math.addExact(expectedRevision, 1L)) {
            throw new IllegalArgumentException("whitelist revision must advance by exactly one");
        }
        UpdateWrapper<McpToolWhitelistEntity> update = new UpdateWrapper<>();
        update.eq("id", after.id())
                .eq("revision", expectedRevision)
                .set("enabled", after.enabled())
                .set("revision", after.revision())
                .set("updateTime", Date.from(clock.instant()));
        return whitelistMapper.update(null, update) == 1;
    }

    @Override
    public boolean delete(long id, long expectedRevision) {
        if (id <= 0 || expectedRevision < 0) {
            return false;
        }
        return whitelistMapper.delete(new LambdaQueryWrapper<McpToolWhitelistEntity>()
                .eq(McpToolWhitelistEntity::getId, id)
                .eq(McpToolWhitelistEntity::getRevision, expectedRevision)) == 1;
    }

    private McpToolWhitelist fromRow(McpToolWhitelistEntity row) {
        return McpToolWhitelist.restore(row.getId(), row.getConnectionId(), row.getToolName(),
                Boolean.TRUE.equals(row.getEnabled()),
                Objects.requireNonNull(row.getRevision(), "revision"));
    }

    private McpToolWhitelistEntity toRow(McpToolWhitelist entry) {
        McpToolWhitelistEntity row = new McpToolWhitelistEntity();
        row.setId(entry.id());
        row.setConnectionId(entry.connectionId());
        row.setToolName(entry.toolName());
        row.setEnabled(entry.enabled());
        row.setRevision(entry.revision());
        Date now = Date.from(clock.instant());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }
}
