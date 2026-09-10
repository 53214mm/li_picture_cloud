package com.li.lipicturecloud.domain.picture;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PictureAssetRepository {
    Optional<PictureAsset> findAssetById(long pictureId);

    /** 某空间在某时刻之后仍存在的图片数（相似图片机会源）。 */
    long countRecentInSpace(long spaceId, Instant since);

    /**
     * 某空间在某时刻之后新增的图片 ID（最新优先、限量、只含已通过审核的图片）。
     * 相似图片机会用它表达"本次机会真正要处理的目标图片"——不能退化成"以前喂养过的那张图"。
     */
    List<Long> findRecentIdsInSpace(long spaceId, Instant since, int limit);
}
