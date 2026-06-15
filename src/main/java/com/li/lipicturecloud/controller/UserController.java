package com.li.lipicturecloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.li.lipicturecloud.annotation.AuthCheck;
import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.common.DeleteRequest;
import com.li.lipicturecloud.common.ResultUtils;
import com.li.lipicturecloud.constant.UserConstant;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.exception.ThrowUtils;
import com.li.lipicturecloud.model.dto.user.UserAddRequest;
import com.li.lipicturecloud.model.dto.user.UserLoginRequest;
import com.li.lipicturecloud.model.dto.user.UserQueryRequest;
import com.li.lipicturecloud.model.dto.user.UserRegisterRequest;
import com.li.lipicturecloud.model.dto.user.UserUpdateRequest;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.vo.UserVO;
import com.li.lipicturecloud.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户控制器
 * <p>
 * 提供用户认证 + 用户管理相关的 RESTful API：
 *
 * <h3>公开接口（无需登录）</h3>
 * <ul>
 *   <li>POST /user/register   —— 用户注册</li>
 *   <li>POST /user/login      —— 用户登录</li>
 * </ul>
 *
 * <h3>登录用户接口</h3>
 * <ul>
 *   <li>POST /user/logout     —— 用户注销</li>
 *   <li>GET  /user/current    —— 获取当前登录用户信息</li>
 *   <li>GET  /user/get/vo     —— 根据 ID 获取脱敏用户信息</li>
 * </ul>
 *
 * <h3>管理员接口（需 admin 角色）</h3>
 * <ul>
 *   <li>POST /user/add        —— 创建用户</li>
 *   <li>POST /user/delete     —— 删除用户</li>
 *   <li>POST /user/update     —— 更新用户</li>
 *   <li>POST /user/list/page  —— 分页获取脱敏用户列表</li>
 *   <li>GET  /user/get        —— 根据 ID 获取完整用户信息（未脱敏）</li>
 * </ul>
 */
@RestController
@RequestMapping("/user")
@Tag(name = "用户管理", description = "用户注册、登录、注销、信息获取以及管理端 CRUD 接口")
public class UserController {

    /**
     * 注入用户服务
     */
    @Resource
    private UserService userService;

    // ============================================================
    // 公开 —— 用户注册
    // ============================================================

    /**
     * 用户注册接口
     * <p>
     * 接收前端提交的注册表单（账号、密码、确认密码），
     * 校验通过后创建用户记录并返回新用户 ID。
     * 注册成功后<b>不自动登录</b>，前端需引导用户跳转到登录页。
     *
     * @param userRegisterRequest 注册请求体，包含 userAccount、userPassword、checkPassword
     * @return 封装了新用户 ID 的统一响应体
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "通过账号和密码注册新用户，注册成功返回用户 ID")
    public BaseResponse<Long> userRegister(
            @RequestBody UserRegisterRequest userRegisterRequest) {

        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR, "请求参数为空");

        long userId = userService.userRegister(userRegisterRequest);
        return ResultUtils.success(userId);
    }

    // ============================================================
    // 公开 —— 用户登录
    // ============================================================

    /**
     * 用户登录接口
     * <p>
     * 接收前端提交的账号和密码，校验通过后创建会话（登录态），
     * 并将脱敏后的用户信息存入 Session 后返回。
     *
     * @param userLoginRequest 登录请求体，包含 userAccount 和 userPassword
     * @param request          HTTP 请求对象，由 Servlet 容器自动注入
     * @return 封装了 UserVO 的统一响应体
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "通过账号和密码进行登录，成功后将用户信息存入 Session")
    public BaseResponse<UserVO> userLogin(
            @RequestBody UserLoginRequest userLoginRequest,
            HttpServletRequest request) {

        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR, "请求参数为空");

        UserVO userVO = userService.userLogin(userLoginRequest, request);
        return ResultUtils.success(userVO);
    }

    // ============================================================
    // 登录用户 —— 用户注销
    // ============================================================

    /**
     * 用户注销接口
     * <p>
     * 清除当前 Session 中的用户登录信息，使登录态失效。
     * 注销后客户端需重新调用登录接口才能获取新的会话。
     *
     * @param request HTTP 请求对象，由 Servlet 容器自动注入
     * @return 封装了操作结果的统一响应体
     */
    @PostMapping("/logout")
    @Operation(summary = "用户注销", description = "清除当前 Session 中的登录态")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {

        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    // ============================================================
    // 登录用户 —— 获取当前登录用户
    // ============================================================

    /**
     * 获取当前登录用户信息
     * <p>
     * 从 Session 中读取登录时存入的用户信息。
     * 前端可在页面初始化时调用此接口，判断用户是否已登录。
     * 若未登录，全局异常处理器会捕获 Service 层抛出的 NOT_LOGIN_ERROR 异常。
     *
     * @param request HTTP 请求对象，由 Servlet 容器自动注入
     * @return 封装了当前登录用户信息的统一响应体
     */
    @GetMapping("/current")
    @Operation(summary = "获取当前登录用户", description = "从 Session 中获取已登录的用户信息，未登录则返回错误")
    public BaseResponse<UserVO> getCurrentUser(HttpServletRequest request) {

        UserVO loginUser = userService.getLoginUser(request);
        return ResultUtils.success(loginUser);
    }

    // ============================================================
    // 登录用户 —— 根据 ID 获取脱敏用户
    // ============================================================

    /**
     * 根据 ID 获取用户（脱敏）
     * <p>
     * 返回 UserVO（不含密码字段），所有登录用户均可调用。
     * 适用于查看他人主页等场景。
     *
     * @param id 用户 ID
     * @return 脱敏后的用户视图对象
     */
    @GetMapping("/get/vo")
    @Operation(summary = "根据 ID 获取脱敏用户", description = "返回不含密码的用户信息，所有登录用户可调用")
    public BaseResponse<UserVO> getUserVOById(@RequestParam("id") long id) {

        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "ID 非法");

        UserVO userVO = userService.getUserVOById(id);
        return ResultUtils.success(userVO);
    }

    // ============================================================
    // 管理员 —— 创建用户
    // ============================================================

    /**
     * 【管理员】创建用户
     * <p>
     * 管理员直接创建用户账号（无需确认密码），创建成功后返回新用户 ID。
     * 权限控制：{@code @AuthCheck(mustRole = "admin")}，由 AuthInterceptor 拦截校验。
     *
     * @param userAddRequest 包含账号、密码及可选个人信息的创建请求
     * @return 新用户 ID
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "【管理员】创建用户", description = "管理员直接创建用户，不需要确认密码")
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {

        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR, "请求参数为空");

        long userId = userService.addUser(userAddRequest);
        return ResultUtils.success(userId);
    }

    // ============================================================
    // 管理员 —— 删除用户
    // ============================================================

    /**
     * 【管理员】根据 ID 删除用户
     * <p>
     * 执行逻辑删除（isDelete 置 1），不会物理删除数据库记录。
     * 权限控制：{@code @AuthCheck(mustRole = "admin")}。
     *
     * @param deleteRequest 包含 id 字段的删除请求
     * @return true 表示删除成功
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "【管理员】删除用户", description = "根据 ID 逻辑删除用户")
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {

        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR, "ID 非法");

        boolean result = userService.deleteUser(deleteRequest.getId());
        return ResultUtils.success(result);
    }

    // ============================================================
    // 管理员 —— 更新用户
    // ============================================================

    /**
     * 【管理员】更新用户信息
     * <p>
     * 仅更新允许修改的字段（昵称、头像、简介、角色），<b>不修改密码</b>。
     * 传入 null 的字段会被跳过，保留数据库原值。
     * 权限控制：{@code @AuthCheck(mustRole = "admin")}。
     *
     * @param userUpdateRequest 包含用户 ID 及待更新字段
     * @return true 表示更新成功
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "【管理员】更新用户", description = "更新用户基本信息（不包含密码）")
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {

        ThrowUtils.throwIf(userUpdateRequest == null, ErrorCode.PARAMS_ERROR, "请求参数为空");

        boolean result = userService.updateUser(userUpdateRequest);
        return ResultUtils.success(result);
    }

    // ============================================================
    // 管理员 —— 分页获取用户列表（脱敏）
    // ============================================================

    /**
     * 【管理员】分页获取用户列表（脱敏）
     * <p>
     * 支持按 ID、账号（模糊）、昵称（模糊）、角色进行组合筛选。
     * 返回分页结果中每条记录为 UserVO（不含密码）。
     * 权限控制：{@code @AuthCheck(mustRole = "admin")}。
     *
     * @param userQueryRequest 分页 + 筛选条件
     * @return 脱敏后的分页用户数据
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "【管理员】分页获取用户列表", description = "支持多条件筛选的分页查询，返回脱敏数据")
    public BaseResponse<IPage<UserVO>> listUserByPage(@RequestBody UserQueryRequest userQueryRequest) {

        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR, "请求参数为空");

        IPage<UserVO> page = userService.listUserByPage(userQueryRequest);
        return ResultUtils.success(page);
    }

    // ============================================================
    // 管理员 —— 根据 ID 获取用户（未脱敏）
    // ============================================================

    /**
     * 【管理员】根据 ID 获取用户（未脱敏）
     * <p>
     * 返回完整的 User 实体（含密码密文、逻辑删除标记等所有字段）。
     * <b>仅供管理后台使用</b>，前端展示请使用 /get/vo 接口。
     * 权限控制：{@code @AuthCheck(mustRole = "admin")}。
     *
     * @param id 用户 ID
     * @return 完整的用户实体
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "【管理员】根据 ID 获取完整用户", description = "返回包含所有字段的用户实体（含密码密文）")
    public BaseResponse<User> getUserById(@RequestParam("id") long id) {

        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "ID 非法");

        User user = userService.getUserById(id);
        return ResultUtils.success(user);
    }
}
