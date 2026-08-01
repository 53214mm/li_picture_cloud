package com.li.lipicturecloud.AI.service;

import com.li.lipicturecloud.model.dto.picture.PictureUploadRequest;
import com.li.lipicturecloud.model.entity.Space;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.vo.PictureVO;
import com.li.lipicturecloud.service.PictureService;
import com.li.lipicturecloud.service.SpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiPictureSaveServiceTest {

    private SpaceService spaceService;
    private PictureService pictureService;
    private AiPictureSaveService service;

    @BeforeEach
    void setUp() {
        spaceService = mock(SpaceService.class);
        pictureService = mock(PictureService.class);
        service = new AiPictureSaveService(spaceService, pictureService);
    }

    @Test
    void returnsLoginMessageAndNeverUploadsWhenUserIsNull() {
        String result = service.save("https://cdn.example.com/image.png", "AI 图片", null);

        assertEquals("无法获取用户信息，请登录后再试。", result);
        verifyNoInteractions(spaceService, pictureService);
    }

    @Test
    void rejectsNonHttpImageUrlWithoutLookingUpSpace() {
        String result = service.save("ftp://cdn.example.com/image.png", "AI 图片", user(42L));

        assertEquals("无效的图片地址", result);
        verifyNoInteractions(spaceService, pictureService);
    }

    @Test
    void returnsCreatePrivateSpaceMessageWhenNoOwnedPrivateSpaceExists() {
        User user = user(42L);
        when(spaceService.getOwnedPrivateSpace(42L)).thenReturn(null);

        String result = service.save("https://cdn.example.com/image.png", "AI 图片", user);

        assertEquals("未保存：请先创建个人空间。", result);
        verify(spaceService).getOwnedPrivateSpace(42L);
        verify(pictureService, never()).uploadPicture(any(), any(), any());
    }

    @Test
    void uploadsToOwnedPrivateSpaceAndReturnsPictureIdAndSpaceName() {
        User user = user(42L);
        Space space = new Space();
        space.setId(8L);
        space.setSpaceName("我的私有空间");
        PictureVO picture = new PictureVO();
        picture.setId(99L);
        when(spaceService.getOwnedPrivateSpace(42L)).thenReturn(space);
        when(pictureService.uploadPicture(eq("https://cdn.example.com/image.png"), any(PictureUploadRequest.class), eq(user)))
                .thenReturn(picture);

        String result = service.save("https://cdn.example.com/image.png", "AI 图片", user);

        ArgumentCaptor<PictureUploadRequest> requestCaptor = ArgumentCaptor.forClass(PictureUploadRequest.class);
        verify(pictureService).uploadPicture(eq("https://cdn.example.com/image.png"), requestCaptor.capture(), eq(user));
        PictureUploadRequest request = requestCaptor.getValue();
        assertEquals(8L, request.getSpaceId());
        assertEquals("https://cdn.example.com/image.png", request.getFileUrl());
        assertEquals("AI 图片", request.getPicName());
        assertEquals("已保存到空间「我的私有空间」，图片 ID: 99", result);
    }

    @Test
    void hidesUploadExceptionDetailsFromTheUser() {
        User user = user(42L);
        Space space = new Space();
        space.setId(8L);
        when(spaceService.getOwnedPrivateSpace(42L)).thenReturn(space);
        when(pictureService.uploadPicture(any(), any(), any()))
                .thenThrow(new IllegalStateException("COS credentials unavailable"));

        String result = service.save("https://cdn.example.com/image.png", "AI 图片", user);

        assertEquals("保存失败，请稍后重试。", result);
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
