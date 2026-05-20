package com.li.lipicturecloud.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户视图对象（脱敏后的用户信息）
 * <p>
 * 登录成功后返回给前端，<b>不包含密码字段</b>，
 * 避免敏感信息暴露在响应中。
 */
@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private Long id;

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像 URL
     */
    private String userAvatar;

    /**
     * 用户简介 / 个人说明
     */
    private String userProfile;

    /**
     * 用户角色：user（普通用户） / admin（管理员）
     */
    private String userRole;

    /**
     * 账号创建时间
     */
    private Date createTime;
}
