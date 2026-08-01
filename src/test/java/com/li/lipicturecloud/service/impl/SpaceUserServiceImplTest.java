package com.li.lipicturecloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.mapper.SpaceUserMapper;
import com.li.lipicturecloud.model.dto.spaceuser.SpaceUserAddRequest;
import com.li.lipicturecloud.model.dto.spaceuser.SpaceUserEditRequest;
import com.li.lipicturecloud.model.entity.Space;
import com.li.lipicturecloud.model.entity.SpaceUser;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.enums.SpaceTypeEnum;
import com.li.lipicturecloud.service.SpaceService;
import com.li.lipicturecloud.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpaceUserServiceImplTest {

    private SpaceUserServiceImpl service;
    private SpaceUserMapper spaceUserMapper;
    private UserService userService;
    private SpaceService spaceService;

    @BeforeEach
    void setUp() {
        service = new SpaceUserServiceImpl();
        spaceUserMapper = mock(SpaceUserMapper.class);
        userService = mock(UserService.class);
        spaceService = mock(SpaceService.class);
        ReflectionTestUtils.setField(service, "baseMapper", spaceUserMapper);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "spaceService", spaceService);
    }

    @Test
    void rejectsDuplicateMember() {
        when(userService.getById(22L)).thenReturn(user(22L));
        when(spaceService.getById(7L)).thenReturn(space(7L, 11L, SpaceTypeEnum.TEAM.getValue()));
        when(spaceUserMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.addSpaceUser(addRequest(7L, 22L, "editor")));

        assertEquals("该用户已在团队中", exception.getMessage());
    }

    @Test
    void rejectsMemberAddedToPrivateSpace() {
        when(userService.getById(22L)).thenReturn(user(22L));
        when(spaceService.getById(7L)).thenReturn(space(7L, 11L, SpaceTypeEnum.PRIVATE.getValue()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.addSpaceUser(addRequest(7L, 22L, "viewer")));

        assertEquals("只有团队空间可以管理成员", exception.getMessage());
    }

    @Test
    void rejectsDeletingCreatorMembership() {
        when(spaceUserMapper.selectById(3L)).thenReturn(member(3L, 7L, 11L, "admin"));
        when(spaceService.getById(7L)).thenReturn(space(7L, 11L, SpaceTypeEnum.TEAM.getValue()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.deleteSpaceUser(3L));

        assertEquals("不能移除团队创建者", exception.getMessage());
    }

    @Test
    void rejectsDemotingCreatorMembership() {
        when(spaceUserMapper.selectById(3L)).thenReturn(member(3L, 7L, 11L, "admin"));
        when(spaceService.getById(7L)).thenReturn(space(7L, 11L, SpaceTypeEnum.TEAM.getValue()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.editSpaceUser(editRequest(3L, "editor")));

        assertEquals("团队创建者必须保留管理员角色", exception.getMessage());
    }

    private static SpaceUserAddRequest addRequest(long spaceId, long userId, String role) {
        SpaceUserAddRequest request = new SpaceUserAddRequest();
        request.setSpaceId(spaceId);
        request.setUserId(userId);
        request.setSpaceRole(role);
        return request;
    }

    private static SpaceUserEditRequest editRequest(long id, String role) {
        SpaceUserEditRequest request = new SpaceUserEditRequest();
        request.setId(id);
        request.setSpaceRole(role);
        return request;
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static Space space(long id, long creatorId, int type) {
        Space space = new Space();
        space.setId(id);
        space.setUserId(creatorId);
        space.setSpaceType(type);
        return space;
    }

    private static SpaceUser member(long id, long spaceId, long userId, String role) {
        SpaceUser member = new SpaceUser();
        member.setId(id);
        member.setSpaceId(spaceId);
        member.setUserId(userId);
        member.setSpaceRole(role);
        return member;
    }
}
