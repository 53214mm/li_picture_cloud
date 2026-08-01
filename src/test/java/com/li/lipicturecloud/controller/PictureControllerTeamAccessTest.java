package com.li.lipicturecloud.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant;
import com.li.lipicturecloud.model.dto.picture.PictureQueryRequest;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.model.entity.Space;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.enums.SpaceTypeEnum;
import com.li.lipicturecloud.model.vo.PictureVO;
import com.li.lipicturecloud.service.PictureService;
import com.li.lipicturecloud.service.SpaceService;
import com.li.lipicturecloud.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PictureControllerTeamAccessTest {

    private PictureController controller;
    private PictureService pictureService;
    private SpaceAuthorizationAccessService authorizationAccessService;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        controller = new PictureController();
        pictureService = mock(PictureService.class);
        SpaceService spaceService = mock(SpaceService.class);
        UserService userService = mock(UserService.class);
        authorizationAccessService = mock(SpaceAuthorizationAccessService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        request = mock(HttpServletRequest.class);

        Space teamSpace = new Space();
        teamSpace.setId(7L);
        teamSpace.setUserId(11L);
        teamSpace.setSpaceType(SpaceTypeEnum.TEAM.getValue());
        User viewer = new User();
        viewer.setId(22L);

        when(spaceService.getById(7L)).thenReturn(teamSpace);
        when(userService.getLoginUserEntity(request)).thenReturn(viewer);
        when(pictureService.getQueryWrapper(any(PictureQueryRequest.class))).thenReturn(new QueryWrapper<>());
        when(pictureService.page(any(Page.class), any(QueryWrapper.class))).thenReturn(new Page<>());
        when(pictureService.getPictureVOPage(any(Page.class), eq(request))).thenReturn(new Page<PictureVO>());
        Picture teamPicture = new Picture();
        teamPicture.setId(9L);
        teamPicture.setSpaceId(7L);
        when(pictureService.getById(9L)).thenReturn(teamPicture);
        when(pictureService.getPictureVO(teamPicture, request)).thenReturn(new PictureVO());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any(String.class))).thenReturn(null);

        ReflectionTestUtils.setField(controller, "pictureService", pictureService);
        ReflectionTestUtils.setField(controller, "spaceService", spaceService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "authorizationAccessService", authorizationAccessService);
        ReflectionTestUtils.setField(controller, "stringRedisTemplate", redisTemplate);
    }

    @Test
    void viewerWithPictureViewPermissionCanListTeamPictures() {
        PictureQueryRequest query = new PictureQueryRequest();
        query.setCurrent(1);
        query.setPageSize(12);
        query.setSpaceId(7L);

        assertDoesNotThrow(() -> controller.listPictureVOByPage(query, request));

        verify(authorizationAccessService).check(
                SpaceUserPermissionConstant.PICTURE_VIEW, 7L, null, null, request);
    }

    @Test
    void viewerWithPictureViewPermissionCanListCachedTeamPictures() {
        PictureQueryRequest query = new PictureQueryRequest();
        query.setCurrent(1);
        query.setPageSize(12);
        query.setSpaceId(7L);

        assertDoesNotThrow(() -> controller.listPictureVOByPageWithCache(query, request));

        verify(authorizationAccessService).check(
                SpaceUserPermissionConstant.PICTURE_VIEW, 7L, null, null, request);
    }

    @Test
    void viewerWithPictureViewPermissionCanOpenTeamPictureDetail() {
        assertDoesNotThrow(() -> controller.getPictureVOById(9L, request));

        verify(authorizationAccessService).check(
                SpaceUserPermissionConstant.PICTURE_VIEW, 7L, null, null, request);
    }
}
