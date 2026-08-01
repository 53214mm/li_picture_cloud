package com.li.lipicturecloud.collaboration.store;

import com.li.lipicturecloud.collaboration.model.CollaborationState;

public record ApplyCollaborationResult(CollaborationState state, boolean newlyApplied) {
}
