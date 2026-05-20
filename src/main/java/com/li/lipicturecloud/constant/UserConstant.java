package com.li.lipicturecloud.constant;

/**
 * 用户模块常量
 * <p>
 * 集中管理用户相关的魔法值，避免硬编码分散在各处。
 * 例如 Session 中的 key、角色值等。
 */
public interface UserConstant {

    /**
     * Session 中保存当前登录用户信息的 key
     * <p>
     * 登录成功后，将 UserVO 存入 HttpSession，key 即为此常量。
     * 后续请求通过该 key 获取登录态。
     */
    String SESSION_USER_KEY = "loginUser";

    /**
     * 默认用户角色 —— 普通用户
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员角色
     */
    String ADMIN_ROLE = "admin";

    /**
     * 密码加密盐值
     * <p>
     * 与用户输入的密码拼接后再进行 MD5 摘要，增加暴力破解难度。
     * 生产环境中应使用更安全的算法（如 BCrypt）。
     */
    String SALT = "liPictureCloud2026";
}
