package com.li.lipicturecloud.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.lipicturecloud.domain.space.SpaceMembership;
import com.li.lipicturecloud.domain.space.SpaceMembershipRepository;
import com.li.lipicturecloud.mapper.SpaceUserMapper;
import com.li.lipicturecloud.model.entity.SpaceUser;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MybatisSpaceMembershipRepository implements SpaceMembershipRepository {

    private final SpaceUserMapper mapper;

    public MybatisSpaceMembershipRepository(SpaceUserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<SpaceMembership> findById(Long membershipId) {
        return Optional.ofNullable(mapper.selectById(membershipId)).map(this::toDomain);
    }

    @Override
    public Optional<SpaceMembership> findBySpaceAndUser(Long spaceId, Long userId) {
        SpaceUser row = mapper.selectOne(new LambdaQueryWrapper<SpaceUser>()
                .eq(SpaceUser::getSpaceId, spaceId)
                .eq(SpaceUser::getUserId, userId));
        return Optional.ofNullable(row).map(this::toDomain);
    }

    private SpaceMembership toDomain(SpaceUser row) {
        return SpaceMembership.restore(row.getId(), row.getSpaceId(), row.getUserId(), row.getSpaceRole());
    }
}
