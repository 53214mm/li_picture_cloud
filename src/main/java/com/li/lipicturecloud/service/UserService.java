package com.li.lipicturecloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.li.lipicturecloud.model.dto.user.UserAddRequest;
import com.li.lipicturecloud.model.dto.user.UserLoginRequest;
import com.li.lipicturecloud.model.dto.user.UserQueryRequest;
import com.li.lipicturecloud.model.dto.user.UserRegisterRequest;
import com.li.lipicturecloud.model.dto.user.UserUpdateRequest;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.vo.UserVO;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户服务接口
 * <p>
 * 继承 MyBatis-Plus 的 IService，自动获得 CRUD 基础能力。
 * 在此之上扩展与用户身份认证相关的业务方法。
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     * <p>
     * 校验参数、检查账号唯一性、加密密码后创建用户记录。
     *
     * @param userRegisterRequest 包含 userAccount、userPassword、checkPassword 的注册请求
     * @return 注册成功返回新用户的 ID
     * @throws com.li.lipicturecloud.exception.BusinessException 参数非法 / 账号已存在时抛出
     */
    long userRegister(UserRegisterRequest userRegisterRequest);

    /**
     * 用户登录
     * <p>
     * 校验账号密码，成功后把用户信息写入 Session 并返回脱敏的 UserVO。
     *
     * @param userLoginRequest 包含 userAccount 和 userPassword 的登录请求
     * @param request          HTTP 请求对象，用于获取 Session
     * @return 脱敏后的用户视图对象
     * @throws com.li.lipicturecloud.exception.BusinessException 账号不存在 / 密码错误时抛出
     */
    UserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    /**
     * 用户注销（退出登录）
     * <p>
     * 清除当前 Session 中的用户信息，使登录态失效。
     *
     * @param request HTTP 请求对象，用于获取 Session
     * @return true 表示注销成功
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取当前登录用户
     * <p>
     * 从 Session 中取出登录态，若未登录则抛出异常。
     * 该方法可被子类的 Controller 复用，实现统一的登录校验。
     *
     * @param request HTTP 请求对象，用于获取 Session
     * @return 当前登录用户的脱敏视图对象
     * @throws com.li.lipicturecloud.exception.BusinessException 未登录时抛出 NOT_LOGIN_ERROR
     */
    UserVO getLoginUser(HttpServletRequest request);

    /**
     * 获取当前登录用户（完整实体）
     * <p>
     * 先从 Session 中取出 UserVO 获取用户 ID，
     * 再从数据库查询完整的 User 实体（含密码密文、审核相关字段等）。
     * <b>仅在需要完整 User 实体的场景使用</b>（如审核时需要 reviewerId），
     * 一般场景请使用 {@link #getLoginUser(HttpServletRequest)}。
     *
     * @param request HTTP 请求对象，用于获取 Session
     * @return 当前登录用户的完整实体
     * @throws com.li.lipicturecloud.exception.BusinessException 未登录 / 用户不存在时抛出
     */
    User getLoginUserEntity(HttpServletRequest request);

    /**
     * 判断当前请求是否已登录
     *
     * @param request HTTP 请求对象
     * @return true 表示已登录
     */
    boolean isLogin(HttpServletRequest request);

    // ============================================================
    // 管理员 —— 用户管理
    // ============================================================

    /**
     * 【管理员】创建用户
     * <p>
     * 由管理员直接创建用户账号，校验规则与注册一致（密码加密、账号唯一性等）。
     *
     * @param userAddRequest 包含账号、密码及可选个人信息的创建请求
     * @return 新用户的 ID
     */
    long addUser(UserAddRequest userAddRequest);

    /**
     * 【管理员】根据 ID 删除用户
     * <p>
     * 执行逻辑删除（MyBatis-Plus 的 @TableLogic 自动将 isDelete 置为 1）。
     *
     * @param id 用户 ID
     * @return true 表示删除成功
     */
    boolean deleteUser(long id);

    /**
     * 【管理员】更新用户信息
     * <p>
     * 仅更新允许修改的字段（昵称、头像、简介、角色），<b>不更新密码</b>。
     * 参数为 null 的字段会被跳过，保留原值。
     *
     * @param userUpdateRequest 包含用户 ID 及待更新字段的请求
     * @return true 表示更新成功
     */
    boolean updateUser(UserUpdateRequest userUpdateRequest);

    /**
     * 【管理员】分页获取用户列表（脱敏）
     * <p>
     * 支持按 ID、账号（模糊）、昵称（模糊）、角色筛选。
     * 返回的每条记录均为 UserVO（不含密码字段）。
     *
     * @param userQueryRequest 分页 + 筛选条件
     * @return 脱敏后的分页结果
     */
    IPage<UserVO> listUserByPage(UserQueryRequest userQueryRequest);

    /**
     * 【管理员】根据 ID 获取用户（未脱敏）
     * <p>
     * 返回完整的 User 实体（含密码密文），仅供管理后台使用。
     *
     * @param id 用户 ID
     * @return 完整的用户实体
     */
    User getUserById(long id);

    /**
     * 根据 ID 获取用户（脱敏）
     * <p>
     * 返回 UserVO（不含密码），供所有登录用户调用。
     *
     * @param id 用户 ID
     * @return 脱敏后的用户视图对象
     */
    UserVO getUserVOById(long id);

    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    /**
     * 是否为管理员（UserVO 版本）
     */
    boolean isAdmin(UserVO userVO);

    /**
     * 是否为管理员（User 实体版本）
     */
    boolean isAdmin(User user);

    UserVO getUserVO(User user);

    List<UserVO> getUserVOList(List<User> userList);
}
