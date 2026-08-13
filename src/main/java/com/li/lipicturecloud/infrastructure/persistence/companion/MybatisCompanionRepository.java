package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.CompanionSkill;
import com.li.lipicturecloud.domain.companion.CompanionStage;
import com.li.lipicturecloud.domain.companion.CompanionTraits;
import com.li.lipicturecloud.mapper.CompanionMapper;
import com.li.lipicturecloud.mapper.CompanionSkillMapper;
import com.li.lipicturecloud.model.entity.CompanionEntity;
import com.li.lipicturecloud.model.entity.CompanionSkillEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisCompanionRepository implements CompanionRepository {

    private final CompanionMapper companionMapper;
    private final CompanionSkillMapper companionSkillMapper;

    public MybatisCompanionRepository(CompanionMapper companionMapper,
                                      CompanionSkillMapper companionSkillMapper) {
        this.companionMapper = companionMapper;
        this.companionSkillMapper = companionSkillMapper;
    }

    @Override
    public Optional<Companion> findByOwnerId(long ownerId) {
        CompanionEntity row = companionMapper.selectOne(new LambdaQueryWrapper<CompanionEntity>()
                .eq(CompanionEntity::getUserId, ownerId));
        return Optional.ofNullable(row).map(this::fromRow);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<Companion> findByOwnerIdForUpdate(long ownerId) {
        // FOR UPDATE 在事务外会立即释放锁；MANDATORY 让错误用法在开发阶段直接暴露。
        CompanionEntity row = companionMapper.selectByUserIdForUpdate(ownerId);
        return Optional.ofNullable(row).map(this::fromRow);
    }

    @Override
    public Companion createIfAbsent(long ownerId, CompanionBalance balance) {
        Optional<Companion> existing = findByOwnerId(ownerId);
        if (existing.isPresent()) {
            return existing.get();
        }

        CompanionEntity row = toRow(Companion.awaken(ownerId, balance));
        try {
            companionMapper.insert(row);
        } catch (DuplicateKeyException raceWonElsewhere) {
            // userId 唯一键是最终仲裁；并发唤醒时输的一方读取赢家创建的同一个伙伴。
            return findByOwnerId(ownerId).orElseThrow(() ->
                    new IllegalStateException("伙伴唯一键冲突后无法读取已存在伙伴", raceWonElsewhere));
        }
        return fromRows(row, List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Companion after, long expectedRevision) {
        Objects.requireNonNull(after, "companion");
        if (after.id() == null) {
            throw new IllegalArgumentException("cannot save an unpersisted companion");
        }
        if (expectedRevision < 0 || after.revision() != Math.addExact(expectedRevision, 1L)) {
            throw new IllegalArgumentException("companion revision must advance by exactly one");
        }
        // revision 是乐观锁第二道保护：即使调用方未互斥，也只有观察到旧版本的一方能写入。
        UpdateWrapper<CompanionEntity> update = new UpdateWrapper<>();
        update.eq("id", after.id())
                .eq("revision", expectedRevision)
                .set("lifeExperience", after.lifeExperience())
                .set("level", after.level())
                .set("lifeStage", after.lifeStage().name())
                .set("curiosity", after.traits().curiosity())
                .set("enthusiasm", after.traits().enthusiasm())
                .set("playfulness", after.traits().playfulness())
                .set("empathy", after.traits().empathy())
                .set("creativity", after.traits().creativity())
                .set("balanceVersion", after.balanceVersion())
                .set("revision", after.revision())
                .set("updateTime", new Date());
        if (companionMapper.update(null, update) != 1) {
            return false;
        }
        upsertSkills(after);
        return true;
    }

    private void upsertSkills(Companion companion) {
        // 技能行按需创建；零经验不占行，读取时会补全为零，保持聚合的技能集合完整。
        for (CompanionSkill skill : CompanionSkill.values()) {
            long expectedExperience = companion.skillExperience().get(skill);
            CompanionSkillEntity existing = companionSkillMapper.selectOne(
                    new LambdaQueryWrapper<CompanionSkillEntity>()
                            .eq(CompanionSkillEntity::getCompanionId, companion.id())
                            .eq(CompanionSkillEntity::getSkillCode, skill.name()));
            if (existing == null) {
                if (expectedExperience == 0) {
                    continue;
                }
                CompanionSkillEntity created = new CompanionSkillEntity();
                created.setCompanionId(companion.id());
                created.setSkillCode(skill.name());
                created.setSkillExperience(expectedExperience);
                try {
                    companionSkillMapper.insert(created);
                } catch (DuplicateKeyException raceWonElsewhere) {
                    updateExistingSkill(companion.id(), skill, expectedExperience, raceWonElsewhere);
                }
            } else if (existing.getSkillExperience() != expectedExperience) {
                existing.setSkillExperience(expectedExperience);
                existing.setUpdateTime(new Date());
                companionSkillMapper.updateById(existing);
            }
        }
    }

    private void updateExistingSkill(long companionId, CompanionSkill skill, long expectedExperience,
                                     DuplicateKeyException raceWonElsewhere) {
        CompanionSkillEntity existing = companionSkillMapper.selectOne(
                new LambdaQueryWrapper<CompanionSkillEntity>()
                        .eq(CompanionSkillEntity::getCompanionId, companionId)
                        .eq(CompanionSkillEntity::getSkillCode, skill.name()));
        if (existing == null) {
            throw new IllegalStateException("伙伴技能唯一键冲突后无法读取已存在技能", raceWonElsewhere);
        }
        if (existing.getSkillExperience() != expectedExperience) {
            existing.setSkillExperience(expectedExperience);
            existing.setUpdateTime(new Date());
            companionSkillMapper.updateById(existing);
        }
    }

    private Companion fromRow(CompanionEntity row) {
        List<CompanionSkillEntity> skills = companionSkillMapper.selectList(
                new LambdaQueryWrapper<CompanionSkillEntity>()
                        .eq(CompanionSkillEntity::getCompanionId, row.getId()));
        return fromRows(row, skills);
    }

    private Companion fromRows(CompanionEntity row, List<CompanionSkillEntity> skillRows) {
        String balanceVersion = Objects.requireNonNull(row.getBalanceVersion(), "balanceVersion");
        if (!CompanionBalance.v1().version().equals(balanceVersion)) {
            throw new IllegalStateException("不支持的伙伴平衡版本: " + balanceVersion);
        }
        Map<CompanionSkill, Long> skills = new EnumMap<>(CompanionSkill.class);
        for (CompanionSkill skill : CompanionSkill.values()) {
            skills.put(skill, 0L);
        }
        for (CompanionSkillEntity skillRow : skillRows) {
            skills.put(CompanionSkill.valueOf(skillRow.getSkillCode()), skillRow.getSkillExperience());
        }
        return Companion.restore(row.getId(), row.getUserId(), row.getLifeExperience(), row.getLevel(),
                CompanionStage.valueOf(row.getLifeStage()), new CompanionTraits(row.getCuriosity(),
                        row.getEnthusiasm(), row.getPlayfulness(), row.getEmpathy(), row.getCreativity()),
                skills, balanceVersion, row.getRevision(), CompanionBalance.v1());
    }

    private CompanionEntity toRow(Companion companion) {
        CompanionEntity row = new CompanionEntity();
        row.setId(companion.id());
        row.setUserId(companion.ownerId());
        row.setLifeExperience(companion.lifeExperience());
        row.setLevel(companion.level());
        row.setLifeStage(companion.lifeStage().name());
        row.setCuriosity(companion.traits().curiosity());
        row.setEnthusiasm(companion.traits().enthusiasm());
        row.setPlayfulness(companion.traits().playfulness());
        row.setEmpathy(companion.traits().empathy());
        row.setCreativity(companion.traits().creativity());
        row.setBalanceVersion(companion.balanceVersion());
        row.setRevision(companion.revision());
        return row;
    }
}
