package com.li.lipicturecloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("companion_proposal")
public class CompanionProposalEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companionId;
    private Long subjectId;
    private String opportunityType;
    private BigDecimal impulseScore;
    private String content;
    private String status;
    private String gateResult;
    private Long revision;
    private Date createTime;
    private Date updateTime;
}
