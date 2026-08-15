package com.li.lipicturecloud.infrastructure.airuntime;

import com.li.lipicturecloud.application.airuntime.FusionArtworkSaveRequest;
import com.li.lipicturecloud.application.airuntime.FusionArtworkSaver;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.model.dto.picture.PictureUploadRequest;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.vo.PictureVO;
import com.li.lipicturecloud.service.PictureService;
import com.li.lipicturecloud.service.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

/**
 * 融合作品回库适配器：把暂存字节经现有图片上传/保存管线入库
 * （空间写权限、额度、审核与可见性规则），返回新作品图片 ID。
 */
@Service
@ConditionalOnProperty(prefix = "app.creation", name = "artwork-stub",
        havingValue = "false", matchIfMissing = true)
public class PicturePipelineFusionArtworkSaver implements FusionArtworkSaver {

    private final PictureService pictureService;
    private final UserService userService;

    public PicturePipelineFusionArtworkSaver(PictureService pictureService, UserService userService) {
        this.pictureService = pictureService;
        this.userService = userService;
    }

    @Override
    public long save(FusionArtworkSaveRequest request) {
        Objects.requireNonNull(request, "request");
        User user = userService.getById(request.userId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户不存在，无法保存作品");
        }
        MultipartFile file = new InMemoryMultipartFile("file", "fusion." + extension(request.mimeType()),
                request.mimeType(), request.bytes());
        PictureUploadRequest upload = new PictureUploadRequest();
        upload.setSpaceId(request.spaceId());
        upload.setPicName(request.name());
        PictureVO picture = pictureService.uploadPicture(file, upload, user);
        return Objects.requireNonNull(picture.getId(), "uploaded picture id");
    }

    private static String extension(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> "webp";
        };
    }
}
