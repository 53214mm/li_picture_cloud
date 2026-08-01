package com.li.lipicturecloud.domain.picture;

public record PictureAsset(Long id, Long ownerId, Long spaceId) {
    public PictureAsset {
        if (id == null || ownerId == null) {
            throw new IllegalArgumentException("图片 ID 和所有者 ID 不能为空");
        }
    }

    public boolean isPublic() {
        return spaceId == null;
    }
}
