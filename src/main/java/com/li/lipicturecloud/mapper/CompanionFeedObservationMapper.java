package com.li.lipicturecloud.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.li.lipicturecloud.application.companion.observation.CompanionFeedObservationRow;
import com.li.lipicturecloud.model.dto.companion.CompanionFeedObservationQueryRequest;
import org.apache.ibatis.annotations.Param;

/** 喂养观测只读投影 Mapper，不暴露任何状态修改能力。 */
public interface CompanionFeedObservationMapper {

    IPage<CompanionFeedObservationRow> selectPage(
            IPage<CompanionFeedObservationRow> page,
            @Param("query") CompanionFeedObservationQueryRequest query);

    CompanionFeedObservationRow selectByRunId(@Param("runId") long runId);
}
