package com.li.lipicturecloud.domain.picture;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PictureAssetRepository {
    Optional<PictureAsset> findAssetById(long pictureId);

    /** 某空间在某时刻之后仍存在的图片数（相似图片机会源）。 */
    long countRecentInSpace(long spaceId, Instant since);

    /**
     * 某空间在某时刻之后新增的图片 ID（最新优先、限量、只含已通过审核的图片），
     * 并可在查询层直接排除一个 ID（相似图片机会排除锚点图，避免"目标 = 旧参照图"）。
     *
     * <p>排除在 SQL 层完成，{@code limit} 因此是"排除后仍取满 limit 张"，
     * 不会因为先取满再丢弃锚点而少一张。</p>
     */
    List<Long> findRecentIdsInSpace(long spaceId, Instant since, int limit, Long excludePictureId);
}
