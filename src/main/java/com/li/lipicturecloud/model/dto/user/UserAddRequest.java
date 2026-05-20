package com.li.lipicturecloud.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 【管理员】创建用户请求 DTO
 * <p>
 * 管理员可通过此接口直接创建用户，无需确认密码。
 * 创建时需指定账号、密码，可选填写昵称、头像、简介、角色。
 */
@Data
public class UserAddRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户账号（必填，唯一标识）
     */
    private String userAccount;

    /**
     * 用户密码（必填，明文传输，后端加密存储）
     */
    private String userPassword;

    /**
     * 用户昵称（选填，不填则默认使用账号名）
     */
    private String userName;

    /**
     * 用户头像 URL（选填）
     */
    private String userAvatar;

    /**
     * 用户简介 / 个人说明（选填）
     */
    private String userProfile;

    /**
     * 用户角色（选填，不填则默认为 "user"）
     * <p>
     * 可选值：user（普通用户）/ admin（管理员）
     */
    private String userRole;
}
