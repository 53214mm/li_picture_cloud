package com.li.lipicturecloud.domain.collaboration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollaborationSessionTest {

    @Test
    void appliesEditingActionsAndIncrementsVersion() {
        CollaborationSession session = CollaborationSession.start(7L);

        session.apply(CollaborationAction.ROTATE_RIGHT, 0);
        session.apply(CollaborationAction.ZOOM_IN, 1);

        assertThat(session.snapshot()).isEqualTo(new CollaborationSnapshot(7L, 90, 1.1, 2));
    }

    @Test
    void rejectsCommandsBasedOnStaleVersion() {
        CollaborationSession session = CollaborationSession.start(7L);
        session.apply(CollaborationAction.ROTATE_LEFT, 0);

        assertThatThrownBy(() -> session.apply(CollaborationAction.ZOOM_OUT, 0))
                .isInstanceOf(StaleCollaborationVersionException.class);
    }

    @Test
    void clampsScaleToSafeRange() {
        CollaborationSession session = CollaborationSession.start(7L);
        for (long version = 0; version < 50; version++) {
            session.apply(CollaborationAction.ZOOM_OUT, version);
        }
        assertThat(session.snapshot().scale()).isEqualTo(0.25);
    }
}
