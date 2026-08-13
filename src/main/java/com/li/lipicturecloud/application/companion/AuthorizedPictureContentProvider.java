package com.li.lipicturecloud.application.companion;

/**
 * 从图片授权上下文取得可外发给视觉模型的受控字节。
 */
public interface AuthorizedPictureContentProvider {

    /**
     * 返回不大于 {@code maxBytes} 的图片内容；实现必须在返回前重新验证权限与资源版本。
     */
    AuthorizedPictureContent load(AuthorizedPictureRef reference, long maxBytes);
}
