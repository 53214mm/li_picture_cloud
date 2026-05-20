package com.li.lipicturecloud.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 【管理员】更新用户请求 DTO
 * <p>
 * 管理员可通过此接口修改指定用户的基本信息（昵称、头像、简介、角色）。
 * <b>注意：不支持修改密码</b>，密码修改应由独立接口处理。
 */
@Data
public class UserUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID（必填，指定要更新哪个用户）
     */
    private Long id;

    /**
     * 用户昵称（选填，为 null 则不更新）
     */
    private String userName;

    /**
     * 用户头像 URL（选填，为 null 则不更新）
     */
    private String userAvatar;

    /**
     * 用户简介 / 个人说明（选填，为 null 则不更新）
     */
    private String userProfile;

    /**
     * 用户角色（选填，为 null 则不更新）
     * <p>
     * 可选值：user（普通用户）/ admin（管理员）
     */
    private String userRole;
}
