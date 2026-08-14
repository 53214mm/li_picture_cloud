package com.li.lipicturecloud.domain.picture;

import java.time.Instant;
import java.util.Optional;

public interface PictureAssetRepository {
    Optional<PictureAsset> findAssetById(long pictureId);
    /** 某空间在某时刻之后仍存在的图片数（相似图片机会源）。 */
    long countRecentInSpace(long spaceId, Instant since);
}
