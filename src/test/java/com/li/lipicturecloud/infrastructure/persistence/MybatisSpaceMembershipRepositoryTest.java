package com.li.lipicturecloud.infrastructure.persistence;

import com.li.lipicturecloud.domain.space.SpaceMemberRole;
import com.li.lipicturecloud.domain.space.SpaceMembership;
import com.li.lipicturecloud.mapper.SpaceUserMapper;
import com.li.lipicturecloud.model.entity.SpaceUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MybatisSpaceMembershipRepositoryTest {

    @Test
    void mapsPersistenceEntityToDomainMembership() {
        SpaceUserMapper mapper = mock(SpaceUserMapper.class);
        SpaceUser row = new SpaceUser();
        row.setId(3L);
        row.setSpaceId(7L);
        row.setUserId(11L);
        row.setSpaceRole("admin");
        when(mapper.selectOne(any())).thenReturn(row);

        SpaceMembership membership = new MybatisSpaceMembershipRepository(mapper)
                .findBySpaceAndUser(7L, 11L)
                .orElseThrow();

        assertThat(membership.role()).isEqualTo(SpaceMemberRole.ADMIN);
    }
}
