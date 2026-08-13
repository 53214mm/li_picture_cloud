package com.li.lipicturecloud.application.companion;

/**
 * 从图片授权上下文取得可外发给视觉模型的受控字节。
 */
public interface AuthorizedPictureContentProvider {

    /**
     * 返回不大于 {@code maxBytes} 的图片内容；实现必须在返回前重新验证权限与资源版本。
     */
    AuthorizedPictureContent load(AuthorizedPictureRef reference, long maxBytes);

    /**
     * 紧贴模型出站前重新验证授权、资源版本和对象绑定。
     *
     * <p>下载完成到 HTTP 出站之间不把长事务或数据库锁带到网络调用中；因此这里是一个短小的
     * TOCTOU 防护检查。实现不得在异常中泄露对象地址、图片字节或 Provider 原文。</p>
     */
    void verifyStillAuthorized(AuthorizedPictureRef reference, AuthorizedPictureContent content);
}
