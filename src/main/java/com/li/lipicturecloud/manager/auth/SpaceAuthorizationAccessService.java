package com.li.lipicturecloud.manager.auth;

import com.li.lipicturecloud.domain.picture.PictureAsset;
import com.li.lipicturecloud.domain.picture.PictureAssetRepository;
import com.li.lipicturecloud.domain.space.SpaceMembership;
import com.li.lipicturecloud.domain.space.SpaceMembershipRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.manager.auth.model.SpaceAuthorizationResource;
import com.li.lipicturecloud.model.entity.Space;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.enums.SpaceTypeEnum;
import com.li.lipicturecloud.service.SpaceService;
import com.li.lipicturecloud.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class SpaceAuthorizationAccessService {

    private final AuthorizationManager authorizationManager;
    private final UserService userService;
    private final SpaceService spaceService;
    private final PictureAssetRepository pictureRepository;
    private final SpaceMembershipRepository membershipRepository;

    public SpaceAuthorizationAccessService(
            AuthorizationManager authorizationManager,
            UserService userService,
            SpaceService spaceService,
            PictureAssetRepository pictureRepository,
            SpaceMembershipRepository membershipRepository
    ) {
        this.authorizationManager = authorizationManager;
        this.userService = userService;
        this.spaceService = spaceService;
        this.pictureRepository = pictureRepository;
        this.membershipRepository = membershipRepository;
    }

    public void check(String permission, Long spaceId, Long pictureId, Long spaceUserId,
                      HttpServletRequest request) {
        if (!getPermissions(spaceId, pictureId, spaceUserId, request).contains(permission)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "缺少权限：" + permission);
        }
    }

    public void checkForUser(String permission, Long pictureId, Long userId) {
        if (!getPermissionsForUser(null, pictureId, null, userId).contains(permission)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "缺少权限：" + permission);
        }
    }

    public void checkSpaceForUser(String permission, Long spaceId, Long userId) {
        if (!getPermissionsForUser(spaceId, null, null, userId).contains(permission)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "缺少权限：" + permission);
        }
    }

    public Set<String> getPermissions(Long spaceId, Long pictureId, Long spaceUserId,
                                      HttpServletRequest request) {
        User loginUser = userService.getLoginUserEntity(request);
        return getPermissions(spaceId, pictureId, spaceUserId, loginUser);
    }

    public Set<String> getPermissionsForUser(Long spaceId, Long pictureId, Long spaceUserId, Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户登录态已失效");
        }
        return getPermissions(spaceId, pictureId, spaceUserId, user);
    }

    private Set<String> getPermissions(Long spaceId, Long pictureId, Long spaceUserId, User loginUser) {
        AuthorizationSubject subject = userService.isAdmin(loginUser)
                ? AuthorizationSubject.platformAdmin(loginUser.getId())
                : AuthorizationSubject.user(loginUser.getId());

        if (spaceUserId != null) {
            SpaceMembership targetMembership = requireMembership(spaceUserId);
            spaceId = targetMembership.spaceId();
        }
        if (pictureId != null) {
            PictureAsset picture = pictureRepository.findAssetById(pictureId).orElse(null);
            if (picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            }
            if (picture.isPublic()) {
                return authorizationManager.getPermissions(
                        subject, SpaceAuthorizationResource.publicPicture(picture.ownerId()));
            }
            spaceId = picture.spaceId();
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

        SpaceMembership membership = findMembership(spaceId, loginUser.getId());
        String role = membership == null ? null : membership.role().value();
        return authorizationManager.getPermissions(subject, SpaceAuthorizationResource.teamSpace(role));
    }

    private SpaceMembership requireMembership(Long membershipId) {
        return membershipRepository.findById(membershipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "空间成员不存在"));
    }

    private SpaceMembership findMembership(Long spaceId, Long userId) {
        return membershipRepository.findBySpaceAndUser(spaceId, userId).orElse(null);
    }
}
