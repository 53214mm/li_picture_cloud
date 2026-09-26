package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;

import java.util.Objects;

/**
 * Controller 传给伙伴应用层的喂养命令。
 *
 * @param subject 服务端登录态解析出的授权主体，不能信任客户端自报的 userId
 * @param pictureId 待喂养的图片 ID
 * @param idempotencyKey 同一次用户操作重试时必须复用的幂等键
 */
public record FeedPictureCommand(AuthorizationSubject subject, long pictureId, String idempotencyKey) {
    public FeedPictureCommand {
        Objects.requireNonNull(subject, "subject");
    }
}
