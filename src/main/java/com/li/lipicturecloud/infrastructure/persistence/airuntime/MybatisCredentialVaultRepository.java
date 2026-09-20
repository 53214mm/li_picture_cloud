package com.li.lipicturecloud.infrastructure.persistence.airuntime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.CredentialVaultRepository;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.mapper.CredentialVaultMapper;
import com.li.lipicturecloud.model.entity.CredentialVaultEntity;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisCredentialVaultRepository implements CredentialVaultRepository {

    private final CredentialVaultMapper vaultMapper;
    private final Clock clock;

    public MybatisCredentialVaultRepository(CredentialVaultMapper vaultMapper, Clock clock) {
        this.vaultMapper = vaultMapper;
        this.clock = clock;
    }

    @Override
    public Optional<CredentialVault> findById(long id) {
        if (id <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(vaultMapper.selectById(id)).map(this::fromRow);
    }

    @Override
    public List<CredentialVault> findByOwnerId(long subjectId) {
        return vaultMapper.selectList(new LambdaQueryWrapper<CredentialVaultEntity>()
                        .eq(CredentialVaultEntity::getSubjectId, subjectId)
                        .orderByAsc(CredentialVaultEntity::getId))
                .stream().map(this::fromRow).toList();
    }

    @Override
    public CredentialVault insert(CredentialVault credential) {
        Objects.requireNonNull(credential, "credential");
        if (credential.id() != null) {
            throw new IllegalArgumentException("cannot insert an already persisted credential");
        }
        CredentialVaultEntity row = toRow(credential);
        vaultMapper.insert(row);
        return credential.withId(Objects.requireNonNull(row.getId(), "assigned credential id"));
    }

    @Override
    public boolean save(CredentialVault after, long expectedRevision) {
        Objects.requireNonNull(after, "credential");
        if (after.id() == null) {
            throw new IllegalArgumentException("cannot save an unpersisted credential");
        }
        if (expectedRevision < 0 || after.revision() != Math.addExact(expectedRevision, 1L)) {
            throw new IllegalArgumentException("credential revision must advance by exactly one");
        }
        UpdateWrapper<CredentialVaultEntity> update = new UpdateWrapper<>();
        update.eq("id", after.id())
                .eq("revision", expectedRevision)
                .set("tail4", after.tail4())
                .set("cipherText", after.cipherText())
                .set("revision", after.revision())
                .set("updateTime", Date.from(clock.instant()));
        return vaultMapper.update(null, update) == 1;
    }

    @Override
    public boolean delete(long id, long expectedRevision) {
        if (id <= 0 || expectedRevision < 0) {
            return false;
        }
        return vaultMapper.delete(new LambdaQueryWrapper<CredentialVaultEntity>()
                .eq(CredentialVaultEntity::getId, id)
                .eq(CredentialVaultEntity::getRevision, expectedRevision)) == 1;
    }

    private CredentialVault fromRow(CredentialVaultEntity row) {
        return CredentialVault.restore(row.getId(), row.getSubjectId(),
                ModelProvider.valueOf(row.getProvider()), row.getTail4(), row.getCipherText(),
                row.getAlgorithm(), Objects.requireNonNull(row.getRevision(), "revision"));
    }

    private CredentialVaultEntity toRow(CredentialVault credential) {
        CredentialVaultEntity row = new CredentialVaultEntity();
        row.setId(credential.id());
        row.setSubjectId(credential.subjectId());
        row.setProvider(credential.provider().name());
        row.setTail4(credential.tail4());
        row.setCipherText(credential.cipherText());
        row.setAlgorithm(credential.algorithm());
        row.setRevision(credential.revision());
        Date now = Date.from(clock.instant());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }
}
