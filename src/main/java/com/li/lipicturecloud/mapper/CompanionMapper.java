package com.li.lipicturecloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.li.lipicturecloud.model.entity.CompanionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CompanionMapper extends BaseMapper<CompanionEntity> {

    @Select("SELECT * FROM companion WHERE userId = #{userId} LIMIT 1 FOR UPDATE")
    CompanionEntity selectByUserIdForUpdate(@Param("userId") long userId);
}
