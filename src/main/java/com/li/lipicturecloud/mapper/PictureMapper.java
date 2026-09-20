package com.li.lipicturecloud.mapper;

import com.li.lipicturecloud.model.entity.Picture;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
* @author MECHREVO
* @description 针对表【picture(图片)】的数据库操作Mapper
* @createDate 2026-06-10 17:23:29
* @Entity com.li.lipicturecloud.model.entity.Picture
*/
public interface PictureMapper extends BaseMapper<Picture> {

    /**
     * 只投影授权与归属所需的列，避免把 URL/名称/简介/标签读入内存；
     * 自定义 SQL 需手写逻辑删除条件（@TableLogic 不作用于自定义语句）。
     */
    @Select("SELECT id, userId, spaceId FROM picture WHERE id = #{id} AND isDelete = 0")
    Picture selectAssetColumns(@Param("id") long id);
}




