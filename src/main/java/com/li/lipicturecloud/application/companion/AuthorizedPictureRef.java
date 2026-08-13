package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;

import java.util.Objects;

public record AuthorizedPictureRef(AuthorizationSubject subject, long pictureId) {
    public AuthorizedPictureRef {
        Objects.requireNonNull(subject, "subject");
        if (pictureId <= 0) {
            throw new IllegalArgumentException("pictureId must be positive");
        }
    }
}
