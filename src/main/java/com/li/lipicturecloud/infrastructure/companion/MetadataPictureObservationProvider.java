package com.li.lipicturecloud.infrastructure.companion;

import com.li.lipicturecloud.application.companion.AuthorizedPictureRef;
import com.li.lipicturecloud.application.companion.PictureObservation;
import com.li.lipicturecloud.application.companion.PictureObservationProvider;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.repository.PictureRepository;
import org.springframework.stereotype.Component;

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;

/**
 * 从站内图片记录提取最小化元数据，不下载图片，也不访问图片像素。
 */
@Component
public class MetadataPictureObservationProvider implements PictureObservationProvider {

    private final PictureRepository pictures;
    private final SpaceAuthorizationAccessService authorization;

    public MetadataPictureObservationProvider(PictureRepository pictures,
                                              SpaceAuthorizationAccessService authorization) {
        this.pictures = pictures;
        this.authorization = authorization;
    }

    @Override
    public PictureObservation observe(AuthorizedPictureRef reference) {
        Picture picture = pictures.findById(reference.pictureId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NO_AUTH_ERROR, "图片不可用或无权访问"));
        // 首次授权与本次读取不是同一查询；提取任何营养事实前再次按当前资源归属校验，
        // 避免图片在两次操作间被移动或撤销共享后仍产生成长。
        authorization.checkForUser(PICTURE_VIEW, picture.getId(), reference.subject().userId());
        Integer width = positive(picture.getPicWidth());
        Integer height = positive(picture.getPicHeight());
        if (width == null || height == null) {
            width = null;
            height = null;
        }
        return new PictureObservation(picture.getId(), hasText(picture.getIntroduction()),
                hasText(picture.getCategory()), width, height, positive(picture.getPicSize()),
                picture.getPicFormat());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Integer positive(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private static Long positive(Long value) {
        return value != null && value > 0 ? value : null;
    }
}
