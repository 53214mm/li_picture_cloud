package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;

import java.util.Objects;

public record FeedPictureCommand(AuthorizationSubject subject, long pictureId, String idempotencyKey) {
    public FeedPictureCommand {
        Objects.requireNonNull(subject, "subject");
    }
}
