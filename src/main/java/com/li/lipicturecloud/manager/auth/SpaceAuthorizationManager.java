package com.li.lipicturecloud.manager.auth;

import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.manager.auth.model.SpaceAuthorizationResource;
import com.li.lipicturecloud.model.enums.SpaceRoleEnum;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.*;

/**
 * 空间授权的最终决策器。
 *
 * <p>{@link SpaceAuthorizationAccessService} 已经把数据库中的图片、空间和成员关系
 * 解析成统一资源；本类只回答一个问题：“这个主体对这种资源拥有哪些权限码？”</p>
 *
 * <p>决策规则分为三类：</p>
 * <ul>
 *     <li>公共图片：所有人可查看，上传者和平台管理员还可编辑、删除；</li>
 *     <li>个人空间：仅空间所有者和平台管理员拥有完整空间权限；</li>
 *     <li>团队空间：根据当前成员的 viewer/editor/admin 角色读取权限配置。</li>
 * </ul>
 */
@Component
public class SpaceAuthorizationManager implements AuthorizationManager {

    // 公共图库没有 SpaceUser 关系，因此直接定义图片所有者拥有的资源权限。
    private static final Set<String> PUBLIC_OWNER_PERMISSIONS = Set.of(
            PICTURE_VIEW, PICTURE_EDIT, PICTURE_DELETE
    );

    // 个人空间只有所有者和平台管理员能进入，这里定义他们可获得的完整权限集合。
    private static final Set<String> PRIVATE_OWNER_PERMISSIONS = Set.of(
            SPACE_VIEW, SPACE_EDIT, SPACE_MANAGE,
            PICTURE_VIEW, PICTURE_UPLOAD, PICTURE_EDIT, PICTURE_DELETE
    );

    private final SpaceUserAuthManager rolePermissionManager;

    public SpaceAuthorizationManager(SpaceUserAuthManager rolePermissionManager) {
        this.rolePermissionManager = rolePermissionManager;
    }

    @Override
    public Set<String> getPermissions(
            AuthorizationSubject subject,
            SpaceAuthorizationResource resource
    ) {
        // 资源解析失败时默认拒绝（空权限集），避免因为上下文缺失而意外放行。
        if (resource == null) {
            return Set.of();
        }
        // 资源类型决定采用哪张权限决策表，调用方无需重复写 if/else。
        return switch (resource.type()) {
            case PUBLIC_PICTURE -> publicPicturePermissions(subject, resource.ownerId());
            case PRIVATE_SPACE -> privateSpacePermissions(subject, resource.ownerId());
            case TEAM_SPACE -> teamSpacePermissions(subject, resource.memberRole());
        };
    }

    private Set<String> publicPicturePermissions(AuthorizationSubject subject, Long ownerId) {
        // 公共图片允许所有用户查看，但只有上传者或平台管理员能够编辑、删除。
        if (subject != null && (subject.platformAdmin() || subject.userId().equals(ownerId))) {
            return PUBLIC_OWNER_PERMISSIONS;
        }
        return Set.of(PICTURE_VIEW);
    }

    private Set<String> privateSpacePermissions(AuthorizationSubject subject, Long ownerId) {
        // 个人空间不接受普通成员关系：既不是所有者、也不是平台管理员时直接返回空集合。
        if (subject == null || (!subject.platformAdmin() && !subject.userId().equals(ownerId))) {
            return Set.of();
        }
        return PRIVATE_OWNER_PERMISSIONS;
    }

    private Set<String> teamSpacePermissions(AuthorizationSubject subject, String memberRole) {
        if (subject == null) {
            return Set.of();
        }
        // 平台管理员在团队空间中按团队 admin 处理；普通用户使用数据库查到的实际成员角色。
        String effectiveRole = subject.platformAdmin()
                ? SpaceRoleEnum.ADMIN.getValue()
                : memberRole;
        // SpaceUserAuthManager 将 viewer/editor/admin 映射为 JSON 配置中的权限码集合。
        // 复制为不可变 Set，既去重，也避免调用方意外修改全局角色配置。
        return Set.copyOf(new LinkedHashSet<>(rolePermissionManager.getPermissionsByRole(effectiveRole)));
    }
}
