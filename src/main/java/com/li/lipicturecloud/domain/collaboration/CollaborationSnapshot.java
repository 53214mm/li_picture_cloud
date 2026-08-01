package com.li.lipicturecloud.domain.collaboration;

public record CollaborationSnapshot(Long pictureId, int rotation, double scale, long version) {
}
