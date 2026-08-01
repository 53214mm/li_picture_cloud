package com.li.lipicturecloud.AI.service;

import com.li.lipicturecloud.model.dto.picture.PictureUploadRequest;
import com.li.lipicturecloud.model.entity.Space;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.vo.PictureVO;
import com.li.lipicturecloud.service.PictureService;
import com.li.lipicturecloud.service.SpaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Saves AI-generated images only to a space owned by the current user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPictureSaveService {

    private final SpaceService spaceService;
    private final PictureService pictureService;

    public String save(String imageUrl, String name, User user) {
        if (user == null) {
            return "无法获取用户信息，请登录后再试。";
        }
        if (imageUrl == null || (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://"))) {
            return "无效的图片地址";
        }
        try {
            Space privateSpace = spaceService.getOwnedPrivateSpace(user.getId());
            if (privateSpace == null) {
                return "未保存：请先创建个人空间。";
            }

            PictureUploadRequest uploadRequest = new PictureUploadRequest();
            uploadRequest.setFileUrl(imageUrl);
            uploadRequest.setPicName(name);
            uploadRequest.setSpaceId(privateSpace.getId());
            PictureVO picture = pictureService.uploadPicture(imageUrl, uploadRequest, user);
            return String.format("已保存到空间「%s」，图片 ID: %s", privateSpace.getSpaceName(), picture.getId());
        } catch (Exception e) {
            log.error("保存 AI 图片到用户私有空间失败", e);
            return "保存失败，请稍后重试。";
        }
    }
}
