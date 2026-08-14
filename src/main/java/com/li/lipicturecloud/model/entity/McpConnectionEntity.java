package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("mcp_connection")
public class McpConnectionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String code;
    private String displayName;
    private String endpointUri;
    private Boolean enabled;
    private Long revision;
    private Date createTime;
    private Date updateTime;
}
