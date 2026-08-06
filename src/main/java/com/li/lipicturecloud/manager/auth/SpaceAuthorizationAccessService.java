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

/**
 * 空间授权的统一访问门面。
 *
 * <p>Controller、Service 和 WebSocket 只需要告诉本类：</p>
 * <ul>
 *     <li>当前用户想执行哪个权限码；</li>
 *     <li>本次操作指向空间、图片、成员关系三种资源中的哪一种。</li>
 * </ul>
 *
 * <p>本类负责继续查询资源归属，把不同形式的 ID 统一转换为
 * {@link SpaceAuthorizationResource}，再交给 {@link AuthorizationManager}
 * 计算权限集合。它解决的是“正在操作什么资源”；具体角色拥有哪些权限，
 * 由 {@link SpaceAuthorizationManager} 决定。</p>
 */
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
        // 先计算当前用户对目标资源拥有的全部权限，再检查是否包含接口要求的权限码。
        if (!getPermissions(spaceId, pictureId, spaceUserId, request).contains(permission)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "缺少权限：" + permission);
        }
    }

    /**
     * 非 HTTP 场景按图片校验权限，例如 WebSocket 消息处理。
     * 此时调用方已经取得 userId，无需再从 HttpServletRequest 读取登录用户。
     */
    public void checkForUser(String permission, Long pictureId, Long userId) {
        if (!getPermissionsForUser(null, pictureId, null, userId).contains(permission)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "缺少权限：" + permission);
        }
    }

    /**
     * 非 HTTP 场景按空间校验权限，例如图片上传 Service 已经知道目标 spaceId。
     */
    public void checkSpaceForUser(String permission, Long spaceId, Long userId) {
        if (!getPermissionsForUser(spaceId, null, null, userId).contains(permission)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "缺少权限：" + permission);
        }
    }

    public Set<String> getPermissions(Long spaceId, Long pictureId, Long spaceUserId,
                                      HttpServletRequest request) {
        // HTTP 调用统一从请求对应的登录态取得完整 User，再进入同一条授权主链。
        User loginUser = userService.getLoginUserEntity(request);
        return getPermissions(spaceId, pictureId, spaceUserId, loginUser);
    }

    public Set<String> getPermissionsForUser(Long spaceId, Long pictureId, Long spaceUserId, Long userId) {
        // WebSocket 等场景只有 userId；先还原授权主体。用户不存在通常意味着登录态已经失效。
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户登录态已失效");
        }
        return getPermissions(spaceId, pictureId, spaceUserId, user);
    }

    /**
     * 把“用户 + 任意一种资源 ID”解析为统一的授权资源。
     *
     * <p>解析顺序是成员关系、图片、空间：</p>
     * <ol>
     *     <li>成员关系 ID 可以查到它属于哪个空间；</li>
     *     <li>图片 ID 可以判断它是公共图片，或继续取得所属空间；</li>
     *     <li>得到空间后，再区分个人空间与团队空间。</li>
     * </ol>
     */
    private Set<String> getPermissions(Long spaceId, Long pictureId, Long spaceUserId, User loginUser) {
        // 平台管理员不是一种团队成员角色，先在授权主体上单独标记，决策层再决定如何放行。
        AuthorizationSubject subject = userService.isAdmin(loginUser)
                ? AuthorizationSubject.platformAdmin(loginUser.getId())
                : AuthorizationSubject.user(loginUser.getId());

        if (spaceUserId != null) {
            // 成员管理接口常只有 SpaceUser.id，需要先反查它属于哪个团队空间。
            SpaceMembership targetMembership = requireMembership(spaceUserId);
            spaceId = targetMembership.spaceId();
        }
        if (pictureId != null) {
            // 图片操作先查询轻量领域对象，避免授权逻辑依赖 Controller 传入不可信的 spaceId。
            PictureAsset picture = pictureRepository.findAssetById(pictureId).orElse(null);
            if (picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            }
            if (picture.isPublic()) {
                // 公共图片没有 spaceId，权限取决于当前用户是否为上传者或平台管理员。
                return authorizationManager.getPermissions(
                        subject, SpaceAuthorizationResource.publicPicture(picture.ownerId()));
            }
            // 非公共图片继续沿图片记录找到所属个人/团队空间。
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
            // 个人空间的关键上下文是空间所有者 ID，不需要查询 SpaceUser 关系。
            return authorizationManager.getPermissions(
                    subject, SpaceAuthorizationResource.privateSpace(space.getUserId()));
        }

        // 团队空间的权限来自“当前用户在当前空间中的角色”；未加入时 role 为 null，最终得到空权限集。
        SpaceMembership membership = findMembership(spaceId, loginUser.getId());
        String role = membership == null ? null : membership.role().value();
        return authorizationManager.getPermissions(subject, SpaceAuthorizationResource.teamSpace(role));
    }

    /**
     * 目标成员关系本身必须存在；不存在属于资源错误，而不是简单的权限不足。
     */
    private SpaceMembership requireMembership(Long membershipId) {
        return membershipRepository.findById(membershipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "空间成员不存在"));
    }

    /**
     * 查询当前用户在指定团队空间中的成员关系。未加入团队时返回 {@code null}。
     */
    private SpaceMembership findMembership(Long spaceId, Long userId) {
        return membershipRepository.findBySpaceAndUser(spaceId, userId).orElse(null);
    }
}
