package com.li.lipicturecloud.manager.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.manager.auth.model.SpaceAuthorizationResource;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.model.entity.Space;
import com.li.lipicturecloud.model.entity.SpaceUser;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.enums.SpaceTypeEnum;
import com.li.lipicturecloud.repository.PictureRepository;
import com.li.lipicturecloud.service.SpaceService;
import com.li.lipicturecloud.service.SpaceUserService;
import com.li.lipicturecloud.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class SpaceAuthorizationAccessService {

    private final AuthorizationManager authorizationManager;
    private final UserService userService;
    private final SpaceService spaceService;
    private final PictureRepository pictureRepository;
    private final SpaceUserService spaceUserService;

    public SpaceAuthorizationAccessService(
            AuthorizationManager authorizationManager,
            UserService userService,
            SpaceService spaceService,
            PictureRepository pictureRepository,
            SpaceUserService spaceUserService
    ) {
        this.authorizationManager = authorizationManager;
        this.userService = userService;
        this.spaceService = spaceService;
        this.pictureRepository = pictureRepository;
        this.spaceUserService = spaceUserService;
    }

    public void check(String permission, Long spaceId, Long pictureId, Long spaceUserId,
                      HttpServletRequest request) {
        if (!getPermissions(spaceId, pictureId, spaceUserId, request).contains(permission)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "缺少权限：" + permission);
        }
    }

    public Set<String> getPermissions(Long spaceId, Long pictureId, Long spaceUserId,
                                      HttpServletRequest request) {
        User loginUser = userService.getLoginUserEntity(request);
        AuthorizationSubject subject = userService.isAdmin(loginUser)
                ? AuthorizationSubject.platformAdmin(loginUser.getId())
                : AuthorizationSubject.user(loginUser.getId());

        if (spaceUserId != null) {
            SpaceUser targetMembership = requireSpaceUser(spaceUserId);
            spaceId = targetMembership.getSpaceId();
        }
        if (pictureId != null) {
            Picture picture = pictureRepository.findById(pictureId).orElse(null);
            if (picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            }
            if (picture.getSpaceId() == null) {
                return authorizationManager.getPermissions(
                        subject, SpaceAuthorizationResource.publicPicture(picture.getUserId()));
            }
            spaceId = picture.getSpaceId();
        }
        if (spaceId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "必须指定权限资源");
        }

        Space space = spaceService.getById(spaceId);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        if (SpaceTypeEnum.PRIVATE.getValue() == space.getSpaceType()) {
            return authorizationManager.getPermissions(
                    subject, SpaceAuthorizationResource.privateSpace(space.getUserId()));
        }

        SpaceUser membership = findMembership(spaceId, loginUser.getId());
        String role = membership == null ? null : membership.getSpaceRole();
        return authorizationManager.getPermissions(subject, SpaceAuthorizationResource.teamSpace(role));
    }

    private SpaceUser requireSpaceUser(Long spaceUserId) {
        SpaceUser membership = spaceUserService.getById(spaceUserId);
        if (membership == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "空间成员不存在");
        }
        return membership;
    }

    private SpaceUser findMembership(Long spaceId, Long userId) {
        return spaceUserService.getOne(new LambdaQueryWrapper<SpaceUser>()
                .eq(SpaceUser::getSpaceId, spaceId)
                .eq(SpaceUser::getUserId, userId));
    }
}
