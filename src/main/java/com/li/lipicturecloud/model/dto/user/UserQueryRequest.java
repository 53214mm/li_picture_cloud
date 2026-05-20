package com.li.lipicturecloud.model.dto.user;

import com.li.lipicturecloud.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 【管理员】用户分页查询请求 DTO
 * <p>
 * 继承 PageRequest 的通用分页字段（current、pageSize、sortField、sortOrder），
 * 并扩展用户特有的筛选条件。所有筛选字段均为选填，不填则不参与过滤。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UserQueryRequest extends PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID（选填，精确匹配）
     */
    private Long id;

    /**
     * 用户账号（选填，模糊搜索）
     */
    private String userAccount;

    /**
     * 用户昵称（选填，模糊搜索）
     */
    private String userName;

    /**
     * 用户角色（选填，精确匹配）
     * <p>
     * 可选值：user / admin
     */
    private String userRole;
}
