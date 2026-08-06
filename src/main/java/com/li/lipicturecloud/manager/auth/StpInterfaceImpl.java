package com.li.lipicturecloud.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.model.entity.Space;
import com.li.lipicturecloud.model.entity.SpaceUser;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.enums.SpaceRoleEnum;
import com.li.lipicturecloud.model.enums.SpaceTypeEnum;
import com.li.lipicturecloud.service.PictureService;
import com.li.lipicturecloud.service.SpaceService;
import com.li.lipicturecloud.service.SpaceUserService;
import com.li.lipicturecloud.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.li.lipicturecloud.constant.UserConstant.SESSION_USER_KEY;

/**
 * Sa-Token 的自定义“权限码加载器”。
 *
 * <p>当代码通过空间账号体系（{@link StpKit#SPACE}）请求 Sa-Token 校验权限时，
 * Sa-Token 会回调 {@link #getPermissionList(Object, String)}，询问当前账号拥有哪些权限码。
 * 本类随后从当前 HTTP 请求中推断正在操作的空间、图片或成员关系，再返回
 * viewer/editor/admin 对应的权限列表。</p>
 *
 * <p>阅读时要把它和新的统一授权门面区分开：</p>
 * <ul>
 *     <li>本类服务于 Sa-Token 的 {@code StpInterface} 回调，需要从请求 URL、参数和 body 推断资源；</li>
 *     <li>{@link SpaceAuthorizationAccessService} 由注解、Service 或 WebSocket 显式传入资源 ID，
 *     再统一解析资源并完成授权。</li>
 * </ul>
 *
 * <p>两条路径使用相同的团队角色权限配置，但入口和上下文来源不同。
 * 理解权限主链时可以先掌握 {@code SpacePermission → SpaceAuthorizationAccessService}，
 * 再阅读本类的请求推断过程。</p>
 */
@Component    // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展
public class StpInterfaceImpl implements StpInterface {
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;
    @Resource
    private SpaceUserService spaceUserService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private UserService userService;
    @Resource
    private PictureService pictureService;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    /**
     * 返回当前空间账号针对“本次请求资源”所拥有的权限码集合。
     *
     * <p>这里返回的不是用户在整个系统中永远拥有的权限，而是结合当前请求资源计算出的权限。
     * 同一个用户在不同团队空间可能角色不同，因此结果也可能不同。</p>
     *
     * @param loginId Sa-Token 当前登录账号 ID
     * @param loginType 账号体系；本类只为空间账号体系提供权限
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 同一个项目可以注册多种 Sa-Token 账号体系；本实现只处理名为 space 的那一套。
        if (!StpKit.SPACE_TYPE.equals(loginType)) {
            return new ArrayList<>();
        }
        // admin 角色拥有配置文件中最完整的空间权限。后续所有“完整放行”都复用这份列表。
        List<String> ADMIN_PERMISSIONS = spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        // Sa-Token 回调只给 loginId，没有直接给 spaceId/pictureId，所以必须从当前 HTTP 请求还原资源上下文。
        SpaceUserAuthContext authContext = getAuthContextByRequest();
        // 没有任何空间资源上下文时，当前实现按无需细分资源的请求处理，返回完整权限集合。
        if (isAllFieldsNull(authContext)) {
            return ADMIN_PERMISSIONS;
        }
        // 获取 userId
        User loginUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(SESSION_USER_KEY);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户未登录");
        }
        Long userId = loginUser.getId();
        // 如果调用方已经提供完整 SpaceUser，上下文已经包含团队角色，可直接做“角色 → 权限码”映射。
        SpaceUser spaceUser = authContext.getSpaceUser();
        if (spaceUser != null) {
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
        // 成员管理接口常只携带目标 SpaceUser.id：先找到目标所属空间，再查询“当前登录用户”在该空间的角色。
        Long spaceUserId = authContext.getSpaceUserId();
        if (spaceUserId != null) {
            spaceUser = spaceUserService.getById(spaceUserId);
            if (spaceUser == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间用户信息");
            }
            // 注意：授权判断使用的是当前登录用户的成员关系，而不是被编辑/删除的目标成员角色。
            SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceUser.getSpaceId())
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (loginSpaceUser == null) {
                return new ArrayList<>();
            }
            // 当前分支按团队成员关系返回权限；没有成员关系时默认拒绝。
            return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
        }
        // 没有成员关系 ID 时，继续尝试从 spaceId 或 pictureId 还原资源归属。
        Long spaceId = authContext.getSpaceId();
        if (spaceId == null) {
            // 图片接口通常只有 pictureId；先查图片，才能知道它属于公共图库还是某个空间。
            Long pictureId = authContext.getPictureId();
            // 连图片 ID 也没有时，当前实现按无需资源细分的请求处理。
            if (pictureId == null) {
                return ADMIN_PERMISSIONS;
            }
            Picture picture = pictureService.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                    .one();
            if (picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到图片信息");
            }
            spaceId = picture.getSpaceId();
            // spaceId 为空表示公共图片：上传者/平台管理员可管理，其他用户只能查看。
            if (spaceId == null) {
                if (picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    // 不是自己的图片，仅可查看
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
        }
        // 已经得到 spaceId，读取空间所有者和空间类型，进入个人/团队两套规则。
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间信息");
        }
        // 个人空间没有 viewer/editor 成员模型，只允许空间所有者或平台管理员操作。
        if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            if (space.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            } else {
                return new ArrayList<>();
            }
        } else {
            // 团队空间必须查询当前用户在当前空间中的成员关系，再按角色加载权限码。
            spaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceId)
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (spaceUser == null) {
                return new ArrayList<>();
            }
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
    }

    private boolean isAllFieldsNull(Object object) {
        if (object == null) {
            return true; // 对象本身为空
        }
        // 获取所有字段并判断是否所有字段都为空
        return Arrays.stream(ReflectUtil.getFields(object.getClass()))
                // 获取字段值
                .map(field -> ReflectUtil.getFieldValue(object, field))
                // 检查是否所有字段都为空
                .allMatch(ObjectUtil::isEmpty);
    }



    /**
     * 返回一个账号所拥有的角色标识集合（权限与角色可分开校验）
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return new ArrayList<>();
    }


    /**
     * 从当前 HTTP 请求中提取参数，构造权限校验上下文对象
     * <p>
     * 核心逻辑：
     * <ol>
     *   <li>通过 {@link RequestContextHolder} 拿到当前请求（ThreadLocal，无需传参）</li>
     *   <li>根据 Content-Type 判断参数来源：
     *       <ul>
     *         <li>{@code application/json} → 读请求体 JSON 反序列化</li>
     *         <li>其他（GET/表单） → 从 {@code request.getParameterMap()} 读取</li>
     *       </ul>
     *   </li>
     *   <li>根据 URL 前缀，将通用的 {@code id} 字段映射到具体的业务 ID：
     *       <ul>
     *         <li>{@code /picture/...} → {@code pictureId}</li>
     *         <li>{@code /spaceUser/...} → {@code spaceUserId}</li>
     *         <li>{@code /space/...} → {@code spaceId}</li>
     *       </ul>
     *       这样后续 Sa-Token 鉴权时就能根据正确的维度（图片 / 空间 / 空间用户）校验权限
     *   </li>
     * </ol>
     *
     * <p>这个方法只负责“从请求猜出资源 ID”，不负责决定用户是否有权限。</p>
     *
     * @return 包含请求参数和业务 ID 映射的权限校验上下文
     * @throws RuntimeException 如果 JSON 请求体读取失败
     */
    private SpaceUserAuthContext getAuthContextByRequest() {
        // ① 从 ThreadLocal 获取当前 HTTP 请求
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        SpaceUserAuthContext authRequest;

        // ② 根据 Content-Type 从请求体或查询/表单参数中恢复 SpaceUserAuthContext。
        if (ContentType.JSON.getValue().equals(contentType)) {
            // POST JSON：body → JSON 反序列化 → SpaceUserAuthContext
            try {
                String body = request.getReader().lines().collect(Collectors.joining("\n"));
                authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);
            } catch (IOException e) {
                throw new RuntimeException("读取请求体失败", e);
            }
        } else {
            // GET/表单：parameterMap → 扁平化（一个 key 取第一个 value）→ BeanUtil 填充
            Map<String, String[]> paramMap = request.getParameterMap();
            Map<String, String> flatMap = paramMap.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()[0]));
            authRequest = BeanUtil.toBean(flatMap, SpaceUserAuthContext.class);
        }

        // ③ URL 前缀 → 业务 ID 映射。因为多个 DTO 都使用通用字段 id，需要结合模块名判断其真实含义。
        //   不同接口的通用参数都叫 "id"，但含义不同：
        //   /picture/delete?id=123  → 123 是图片 ID
        //   /space/delete?id=456    → 456 是空间 ID
        Long id = authRequest.getId();
        if (ObjUtil.isNotNull(id)) {
            // 去掉 contextPath 前缀和首斜杠，取第一段作为模块名
            String requestUri = request.getRequestURI();
            String partUri = requestUri.replace(contextPath + "/", "");
            String moduleName = StrUtil.subBefore(partUri, "/", false);
            switch (moduleName) {
                case "picture":
                    authRequest.setPictureId(id);
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id);
                    break;
                case "space":
                    authRequest.setSpaceId(id);
                    break;
                default:
            }
        }
        return authRequest;
    }


}
