package com.li.lipicturecloud.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录请求 DTO
 * <p>
 * 封装前端提交的登录表单数据，包含账号和密码两个字段。
 * 在 Controller 层接收请求体后传入 Service 层进行校验与认证。
 */
@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户账号（唯一标识，登录时由用户输入）
     */
    private String userAccount;

    /**
     * 用户密码（登录时由用户输入，明文传输，后端进行加密后与数据库密文比对）
     */
    private String userPassword;
}
