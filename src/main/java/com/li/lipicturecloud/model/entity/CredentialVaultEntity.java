package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("credential_vault")
public class CredentialVaultEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long subjectId;
    private String provider;
    private String tail4;
    private String cipherText;
    private String algorithm;
    private Long revision;
    private Date createTime;
    private Date updateTime;
}
