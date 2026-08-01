package com.li.lipicturecloud.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.lipicturecloud.constant.UserConstant;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.StpKit;
import com.li.lipicturecloud.mapper.UserMapper;
import com.li.lipicturecloud.model.dto.user.UserAddRequest;
import com.li.lipicturecloud.model.dto.user.UserLoginRequest;
import com.li.lipicturecloud.model.dto.user.UserQueryRequest;
import com.li.lipicturecloud.model.dto.user.UserRegisterRequest;
import com.li.lipicturecloud.model.dto.user.UserUpdateRequest;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.enums.UserRoleEnum;
import com.li.lipicturecloud.model.vo.UserVO;
import com.li.lipicturecloud.service.PasswordHashService;
import com.li.lipicturecloud.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.li.lipicturecloud.constant.UserConstant.SESSION_USER_KEY;

/**
 * 用户服务实现类
 * <p>
 * 继承 MyBatis-Plus 的 ServiceImpl，自动获得通用 CRUD 实现。
 * 重点实现用户登录、注销等认证相关逻辑。
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Resource
    private PasswordHashService passwordHashService;

    // ============================================================
    // 账号与密码长度下限（注册时校验）
    // ============================================================

    /** 账号最小长度 */
    private static final int MIN_ACCOUNT_LENGTH = 4;

    /** 密码最小长度 */
    private static final int MIN_PASSWORD_LENGTH = 8;

    /**
     * 用户注册
     * <p>
     * 注册流程：
     * <ol>
     *   <li><b>参数校验</b> —— 账号、密码、确认密码均不能为空；长度需满足下限</li>
     *   <li><b>一致性校验</b> —— 两次输入的密码必须一致</li>
     *   <li><b>唯一性校验</b> —— 同一账号只能注册一次</li>
     *   <li><b>密码加密</b> —— 使用 BCrypt 生成密码哈希并存入数据库</li>
     *   <li><b>持久化</b> —— 构造 User 实体并调用 MyBatis-Plus 的 save 方法写入数据库</li>
     *   <li><b>返回 ID</b> —— 返回数据库自增（或雪花算法）生成的主键</li>
     * </ol>
     *
     * @param userRegisterRequest 包含 userAccount、userPassword、checkPassword 的注册请求
     * @return 新用户的 ID
     * @throws BusinessException 参数非法 / 两次密码不一致 / 账号已存在时抛出
     */
    @Override
    public long userRegister(UserRegisterRequest userRegisterRequest) {
        // ============================================================
        // 第 1 步：提取参数
        // ============================================================
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();

        // ============================================================
        // 第 2 步：参数非空校验
        // ============================================================
        // 账号不能为空白（null、空串、纯空格均拦截）
        if (StrUtil.isBlank(userAccount)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号不能为空");
        }
        // 密码不能为空白
        if (StrUtil.isBlank(userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码不能为空");
        }
        // 确认密码不能为空白
        if (StrUtil.isBlank(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "确认密码不能为空");
        }

        // ============================================================
        // 第 3 步：长度校验
        // ============================================================
        // 账号长度至少 4 位，防止过短的账号名
        if (userAccount.length() < MIN_ACCOUNT_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度不能小于" + MIN_ACCOUNT_LENGTH + "位");
        }
        // 密码长度至少 8 位，提高安全性
        if (userPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于" + MIN_PASSWORD_LENGTH + "位");
        }

        // ============================================================
        // 第 4 步：两次密码一致性校验
        // ============================================================
        // 两次输入的密码必须完全相同（区分大小写）
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }

        // ============================================================
        // 第 5 步：账号唯一性校验
        // ============================================================
        // 查询数据库中是否已存在该账号（逻辑删除的数据已被 MyBatis-Plus 自动过滤）
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserAccount, userAccount);
        long count = this.count(queryWrapper);
        if (count > 0) {
            // 账号已被注册 —— 拒绝重复注册
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该账号已被注册");
        }

        // ============================================================
        // 第 6 步：密码加密
        // ============================================================
        String encryptedPassword = passwordHashService.encode(userPassword);

        // ============================================================
        // 第 7 步：构造用户实体并持久化
        // ============================================================
        User user = new User();
        user.setUserAccount(userAccount);
        // 存入数据库的必须是加密后的密文，绝不能存明文！
        user.setUserPassword(encryptedPassword);
        // 昵称默认使用账号名
        user.setUserName(userAccount);
        // 赋予默认角色：普通用户
        user.setUserRole(UserConstant.DEFAULT_ROLE);

        // MyBatis-Plus 的 save 方法：自动填充主键（雪花算法，由 @TableId(type = IdType.ASSIGN_ID) 控制）
        boolean saved = this.save(user);
        if (!saved) {
            // 理论上不会走到这里，save 失败时 MyBatis-Plus 会直接抛异常
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，请稍后重试");
        }

        // ============================================================
        // 第 8 步：返回新用户 ID
        // ============================================================
        return user.getId();
    }

    /**
     * 用户登录
     * <p>
     * 登录流程：
     * <ol>
     *   <li><b>参数校验</b> —— 账号和密码均不能为空</li>
     *   <li><b>查询用户</b> —— 根据 userAccount 在数据库中查询用户记录</li>
     *   <li><b>密码比对</b> —— 使用 BCrypt 验证前端传来的明文密码</li>
     *   <li><b>写入会话</b> —— 密码验证通过后，将脱敏的 UserVO 存入 HttpSession</li>
     *   <li><b>返回结果</b> —— 返回 UserVO 给前端</li>
     * </ol>
     *
     * @param userLoginRequest 包含 userAccount 和 userPassword 的登录请求
     * @param request          HTTP 请求对象，用于获取/创建 Session
     * @return 脱敏后的用户视图对象
     * @throws BusinessException 参数为空 / 账号不存在 / 密码错误时抛出
     */
    @Override
    public UserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request) {
        // ============================================================
        // 第 1 步：参数校验
        // ============================================================
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();

        // 账号不能为空 —— 空白字符串（null、""、纯空格）均视为无效
        if (StrUtil.isBlank(userAccount)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号不能为空");
        }
        // 密码不能为空 —— 空白字符串（null、""、纯空格）均视为无效
        if (StrUtil.isBlank(userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码不能为空");
        }

        // ============================================================
        // 第 2 步：查询用户
        // ============================================================
        // 构建查询条件：user_account = 前端传入的账号
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserAccount, userAccount);

        // 调用 MyBatis-Plus 的 getOne 查询单条记录
        User user = this.getOne(queryWrapper);
        if (user == null) {
            // 账号不存在 —— 抛出业务异常，由全局异常处理器统一捕获
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }

        // ============================================================
        // 第 3 步：密码比对
        // ============================================================
        if (!passwordHashService.matches(userPassword, user.getUserPassword())) {
            // 密码不匹配 —— 抛出业务异常
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }

        // ============================================================
        // 第 4 步：写入会话（登录态）
        // ============================================================
        // 将 User 实体转换为 UserVO（脱敏，不包含密码）
        UserVO userVO = convertToVO(user);

        // 获取（或创建）HttpSession，将 UserVO 存入 Session
        // 存入的 key 为 SESSION_USER_KEY = "loginUser"
        HttpSession session = request.getSession();
        session.setAttribute(SESSION_USER_KEY, userVO);

        //记录用户登录态到 Sa-token，便于空间鉴权时使用，注意保证该用户信息与 SpringSession 中的信息过期时间一致
        StpKit.SPACE.login(user.getId());
        StpKit.SPACE.getSession().set(SESSION_USER_KEY, user);

        // ============================================================
        // 第 5 步：返回脱敏用户信息
        // ============================================================

        return userVO;
    }

    /**
     * 用户注销（退出登录）
     * <p>
     * 注销流程：
     * <ol>
     *   <li>获取当前 Session（不新建，仅获取已有的）</li>
     *   <li>移除 Session 中 key 为 "loginUser" 的属性</li>
     *   <li>标记登录态失效</li>
     * </ol>
     *
     * @param request HTTP 请求对象
     * @return true 表示注销成功
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 获取已有的 Session，传入 false 表示不自动创建新 Session
        HttpSession session = request.getSession(false);
        if (session != null) {
            // 移除 Session 中的用户信息，使登录态失效
            session.removeAttribute(SESSION_USER_KEY);
        }
        // 无论 Session 是否存在，注销操作均视为成功
        return true;
    }

    /**
     * 获取当前登录用户
     * <p>
     * 从 Session 中取出之前登录时存入的 UserVO。
     * 若 Session 不存在或未找到登录信息，则抛出"未登录"业务异常。
     *
     * @param request HTTP 请求对象
     * @return 当前登录用户的脱敏视图对象
     * @throws BusinessException 未登录时抛出 NOT_LOGIN_ERROR
     */
    @Override
    public UserVO getLoginUser(HttpServletRequest request) {
        // 获取已有的 Session（不创建新的）
        HttpSession session = request.getSession(false);
        if (session == null) {
            // Session 不存在，说明从未登录或会话已过期
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 从 Session 中取出用户信息（登录时存入的是 UserVO）
        Object userObj = session.getAttribute(SESSION_USER_KEY);
        if (userObj == null) {
            // Session 存在但没有用户信息，说明未登录
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        return (UserVO) userObj;
    }

    /**
     * 获取当前登录用户（完整实体）
     * <p>
     * 先从 Session 中取出 UserVO 获取用户 ID，
     * 再从数据库查询完整的 User 实体。
     * 适用场景：图片审核（需要 reviewerId）等需要完整 User 数据的操作。
     */
    @Override
    public User getLoginUserEntity(HttpServletRequest request) {
        // 复用 getLoginUser 获取 Session 中的 UserVO，拿到用户 ID
        UserVO userVO = getLoginUser(request);
        // 根据 ID 从数据库查询完整 User 实体
        User user = this.getById(userVO.getId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        return user;
    }

    /**
     * 判断当前请求是否已登录
     *
     * @param request HTTP 请求对象
     * @return true 表示已登录
     */
    @Override
    public boolean isLogin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        return session.getAttribute(SESSION_USER_KEY) != null;
    }

    // ============================================================
    // 管理员 —— 用户管理
    // ============================================================

    /**
     * 【管理员】创建用户
     * <p>
     * 创建流程与注册类似，但<b>不需要确认密码</b>：
     * <ol>
     *   <li>参数校验 —— 账号、密码不能为空且满足长度下限</li>
     *   <li>唯一性校验 —— 同一账号只能存在一次</li>
     *   <li>密码加密 —— 使用 BCrypt 生成密码哈希</li>
     *   <li>构造实体 —— 填充账号、密文密码、可选个人信息、角色</li>
     *   <li>持久化 —— MyBatis-Plus save</li>
     *   <li>返回 ID</li>
     * </ol>
     *
     * @param userAddRequest 管理员填写的创建用户请求
     * @return 新用户的 ID
     */
    @Override
    public long addUser(UserAddRequest userAddRequest) {
        String userAccount = userAddRequest.getUserAccount();
        String userPassword = userAddRequest.getUserPassword();

        // ---- 参数非空校验 ----
        if (StrUtil.isBlank(userAccount)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号不能为空");
        }
        if (StrUtil.isBlank(userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码不能为空");
        }

        // ---- 长度校验 ----
        if (userAccount.length() < MIN_ACCOUNT_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度不能小于" + MIN_ACCOUNT_LENGTH + "位");
        }
        if (userPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于" + MIN_PASSWORD_LENGTH + "位");
        }

        // ---- 唯一性校验 ----
        if (this.count(new LambdaQueryWrapper<User>().eq(User::getUserAccount, userAccount)) > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该账号已被注册");
        }

        // ---- 密码加密 ----
        String encryptedPassword = passwordHashService.encode(userPassword);

        // ---- 构造 User 实体 ----
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptedPassword);
        // 昵称：优先使用传入值，否则用账号名兜底
        user.setUserName(StrUtil.isNotBlank(userAddRequest.getUserName()) ? userAddRequest.getUserName() : userAccount);
        user.setUserAvatar(userAddRequest.getUserAvatar());
        user.setUserProfile(userAddRequest.getUserProfile());
        // 角色：优先使用传入值（且必须在枚举范围内），否则默认 user
        String role = userAddRequest.getUserRole();
        user.setUserRole(UserRoleEnum.getEnumByValue(role) != null ? role : UserConstant.DEFAULT_ROLE);

        // ---- 持久化 ----
        this.save(user);
        return user.getId();
    }

    /**
     * 【管理员】根据 ID 删除用户
     * <p>
     * 调用 MyBatis-Plus 的 removeById，配合 @TableLogic 自动执行逻辑删除
     * （将 isDelete 置为 1），不会物理删除数据。
     *
     * @param id 用户 ID
     * @return true 表示删除成功
     */
    @Override
    public boolean deleteUser(long id) {
        // 检查用户是否存在（逻辑删除状态下查不到）
        User user = this.getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        // 逻辑删除
        return this.removeById(id);
    }

    /**
     * 【管理员】更新用户信息
     * <p>
     * 仅更新允许修改的字段：昵称、头像、简介、角色。
     * <b>不会更新密码</b>，参数为 null 的字段会被跳过以保留数据库原值。
     * <p>
     * 使用 MyBatis-Plus 的 updateById，由于实体上 @TableLogic 标注了 isDelete，
     * 已删除的用户无法被更新（updateById 会拼接 WHERE isDelete=0）。
     *
     * @param userUpdateRequest 包含用户 ID 及待更新字段
     * @return true 表示更新成功
     */
    @Override
    public boolean updateUser(UserUpdateRequest userUpdateRequest) {
        Long id = userUpdateRequest.getId();
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        }

        // 查询原记录，确保用户存在
        User user = this.getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        // 仅覆盖非 null 的字段，保留 null 字段的原值
        if (StrUtil.isNotBlank(userUpdateRequest.getUserName())) {
            user.setUserName(userUpdateRequest.getUserName());
        }
        if (StrUtil.isNotBlank(userUpdateRequest.getUserAvatar())) {
            user.setUserAvatar(userUpdateRequest.getUserAvatar());
        }
        if (StrUtil.isNotBlank(userUpdateRequest.getUserProfile())) {
            user.setUserProfile(userUpdateRequest.getUserProfile());
        }
        if (StrUtil.isNotBlank(userUpdateRequest.getUserRole())) {
            String role = userUpdateRequest.getUserRole();
            // 校验角色值必须在枚举范围内
            if (UserRoleEnum.getEnumByValue(role) == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的用户角色");
            }
            user.setUserRole(role);
        }

        // updateById：MyBatis-Plus 会根据主键 id 执行 UPDATE，并自动拼接乐观锁/逻辑删除条件
        return this.updateById(user);
    }

    /**
     * 【管理员】分页获取用户列表（脱敏）
     * <p>
     * 支持按 ID（精确）、账号（模糊）、昵称（模糊）、角色（精确）组合筛选。
     * 返回结果中每条记录均为 UserVO，密码字段被排除。
     *
     * @param userQueryRequest 分页 + 筛选条件
     * @return IPage&lt;UserVO&gt; 脱敏分页结果
     */
    @Override
    public IPage<UserVO> listUserByPage(UserQueryRequest userQueryRequest) {
        // ---- 构建分页对象 ----
        Page<User> page = new Page<>(userQueryRequest.getCurrent(), userQueryRequest.getPageSize());

        // ---- 构建查询条件 ----
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        // id 精确匹配
        wrapper.eq(userQueryRequest.getId() != null, User::getId, userQueryRequest.getId());
        // 账号模糊搜索
        wrapper.like(StrUtil.isNotBlank(userQueryRequest.getUserAccount()), User::getUserAccount, userQueryRequest.getUserAccount());
        // 昵称模糊搜索
        wrapper.like(StrUtil.isNotBlank(userQueryRequest.getUserName()), User::getUserName, userQueryRequest.getUserName());
        // 角色精确匹配
        wrapper.eq(StrUtil.isNotBlank(userQueryRequest.getUserRole()), User::getUserRole, userQueryRequest.getUserRole());
        // 按创建时间降序排列
        wrapper.orderByDesc(User::getCreateTime);

        // ---- 执行分页查询 ----
        IPage<User> userPage = this.page(page, wrapper);

        // ---- 转换为 UserVO 列表 ----
        List<UserVO> voList = userPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // ---- 构造返回的分页对象 ----
        // 创建一个新的 Page 对象，拷贝原分页信息但使用 VO 列表
        Page<UserVO> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 【管理员】根据 ID 获取用户（未脱敏）
     * <p>
     * 返回完整的 User 实体（含密码密文、逻辑删除标记等所有字段）。
     * <b>仅供管理后台使用</b>，普通接口请使用 {@link #getUserVOById(long)}。
     *
     * @param id 用户 ID
     * @return 完整的用户实体，不存在时返回 null
     */
    @Override
    public User getUserById(long id) {
        User user = this.getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        return user;
    }

    /**
     * 根据 ID 获取用户（脱敏）
     * <p>
     * 返回 UserVO（不含密码），所有登录用户均可调用。
     *
     * @param id 用户 ID
     * @return 脱敏后的用户视图对象
     */
    @Override
    public UserVO getUserVOById(long id) {
        User user = this.getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        return convertToVO(user);
    }

    /**
     * 将 User 实体转换为 UserVO（脱敏）
     * <p>
     * 使用 Hutool 的 BeanUtil.copyProperties 进行属性拷贝，
     * 源对象中有而目标对象中没有的属性会被自动忽略（如 userPassword）。
     *
     * @param user 数据库中的用户实体（包含密码等敏感字段）
     * @return 脱敏后的用户视图对象
     */
    private UserVO convertToVO(User user) {
        UserVO userVO = new UserVO();
        // Hutool BeanUtil：自动拷贝同名同类型的属性
        // user 中的 userPassword 在 UserVO 中没有对应字段，会被自动忽略
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public boolean isAdmin(UserVO userVO) {
        return userVO != null && UserRoleEnum.ADMIN.getValue().equals(userVO.getUserRole());
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

}
