package com.li.lipicturecloud.collaboration.store;

import com.li.lipicturecloud.collaboration.model.CollaborationCommand;
import com.li.lipicturecloud.collaboration.model.CollaborationState;

public interface CollaborationStateStore {
    CollaborationState current(Long pictureId);

    ApplyCollaborationResult apply(CollaborationCommand command);

    int activeSessionCount();
}
