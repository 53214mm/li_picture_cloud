package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.companion.view.CompanionHomeView;
import com.li.lipicturecloud.application.companion.view.FeedPictureResult;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;

public interface CompanionLife {
    CompanionHomeView home(AuthorizationSubject subject);
    CompanionHomeView awaken(AuthorizationSubject subject);
    FeedPictureResult feed(FeedPictureCommand command);
}
