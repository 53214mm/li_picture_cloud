package com.li.lipicturecloud.domain.picture;

import java.util.Optional;

public interface PictureAssetRepository {
    Optional<PictureAsset> findAssetById(long pictureId);
}
