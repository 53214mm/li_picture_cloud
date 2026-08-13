package com.li.lipicturecloud.application.companion;

/**
 * 把“如何读取图片”藏在伙伴用例之外的端口。
 * 实现可以读取本地元数据，也可以在以后调用具备 imageInput 能力的视觉模型。
 */
@FunctionalInterface
public interface PictureObservationProvider {
    PictureObservation observe(AuthorizedPictureRef picture);
}
