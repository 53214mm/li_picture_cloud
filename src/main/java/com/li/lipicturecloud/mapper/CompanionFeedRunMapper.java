package com.li.lipicturecloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.li.lipicturecloud.model.entity.CompanionFeedRunEntity;

/**
 * 喂养执行表的 MyBatis-Plus Mapper。
 * 幂等和状态迁移约束由 Repository 组合条件实现，本接口只提供底层数据库操作能力。
 */
public interface CompanionFeedRunMapper extends BaseMapper<CompanionFeedRunEntity> {
}
