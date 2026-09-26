package com.li.lipicturecloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.li.lipicturecloud.model.entity.CompanionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 伙伴主表 Mapper，除常规 CRUD 外还提供事务内加行锁的查询。 */
public interface CompanionMapper extends BaseMapper<CompanionEntity> {

    /** 必须在事务中使用；锁定用户的伙伴行，供一次成长操作独占修改当前状态。 */
    @Select("SELECT * FROM companion WHERE userId = #{userId} LIMIT 1 FOR UPDATE")
    CompanionEntity selectByUserIdForUpdate(@Param("userId") long userId);
}
