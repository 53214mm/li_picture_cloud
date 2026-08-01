package com.li.lipicturecloud.collaboration;

import com.li.lipicturecloud.collaboration.model.CollaborationCommand;
import com.li.lipicturecloud.collaboration.model.CollaborationOperation;
import com.li.lipicturecloud.collaboration.model.CollaborationState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollaborationSessionServiceTest {

    private final CollaborationSessionService service = new CollaborationSessionService();

    @Test
    void appliesRotateAndZoomCommandsToServerState() {
        CollaborationState rotated = service.apply(command("one", CollaborationOperation.ROTATE_RIGHT, 0));
        CollaborationState zoomed = service.apply(command("two", CollaborationOperation.ZOOM_IN, 1));

        assertThat(rotated.rotation()).isEqualTo(90);
        assertThat(rotated.version()).isEqualTo(1);
        assertThat(zoomed.scale()).isEqualTo(1.1);
        assertThat(zoomed.version()).isEqualTo(2);
    }

    @Test
    void duplicateCommandIsIdempotent() {
        CollaborationCommand command = command("same", CollaborationOperation.ROTATE_LEFT, 0);
        CollaborationState first = service.apply(command);
        CollaborationState duplicate = service.apply(command);

        assertThat(duplicate).isEqualTo(first);
        assertThat(service.current(7L).version()).isEqualTo(1);
    }

    @Test
    void rejectsStaleBaseVersion() {
        service.apply(command("one", CollaborationOperation.ZOOM_OUT, 0));

        assertThatThrownBy(() -> service.apply(command("two", CollaborationOperation.ZOOM_OUT, 0)))
                .isInstanceOf(CollaborationVersionConflictException.class);
    }

    private CollaborationCommand command(String id, CollaborationOperation operation, long version) {
        return new CollaborationCommand(id, 7L, 99L, operation, version);
    }
}
