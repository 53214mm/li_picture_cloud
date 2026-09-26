package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;

import java.util.Objects;

/**
 * 已通过登录态识别的主体与待分析图片的轻量引用。
 *
 * <p>这里只携带身份和图片 ID，不读取图片内容；具体的权限复核与内容加载由应用端口完成。</p>
 */
public record AuthorizedPictureRef(AuthorizationSubject subject, long pictureId) {
    public AuthorizedPictureRef {
        Objects.requireNonNull(subject, "subject");
        if (pictureId <= 0) {
            throw new IllegalArgumentException("pictureId must be positive");
        }
    }
}
