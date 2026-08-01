package com.li.lipicturecloud.collaboration.store;

import com.li.lipicturecloud.collaboration.CollaborationVersionConflictException;
import com.li.lipicturecloud.collaboration.model.CollaborationCommand;
import com.li.lipicturecloud.collaboration.model.CollaborationOperation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryCollaborationStateStoreTest {

    private final CollaborationStateStore store = new InMemoryCollaborationStateStore();

    @Test
    void initializesAndKeepsPicturesIndependent() {
        assertThat(store.current(7L).version()).isZero();
        store.apply(command("a", 7L, CollaborationOperation.ROTATE_RIGHT, 0));

        assertThat(store.current(7L).rotation()).isEqualTo(90);
        assertThat(store.current(8L).rotation()).isZero();
        assertThat(store.activeSessionCount()).isEqualTo(2);
    }

    @Test
    void appliesEverySupportedOperation() {
        assertThat(store.apply(command("a", 7L, CollaborationOperation.ROTATE_LEFT, 0)).state().rotation())
                .isEqualTo(270);
        assertThat(store.apply(command("b", 7L, CollaborationOperation.ROTATE_RIGHT, 1)).state().rotation())
                .isZero();
        assertThat(store.apply(command("c", 7L, CollaborationOperation.ZOOM_IN, 2)).state().scale())
                .isEqualTo(1.1);
        assertThat(store.apply(command("d", 7L, CollaborationOperation.ZOOM_OUT, 3)).state().scale())
                .isEqualTo(1.0);
    }

    @Test
    void returnsStoredResultForDuplicateCommand() {
        CollaborationCommand command = command("same", 7L, CollaborationOperation.ZOOM_IN, 0);

        ApplyCollaborationResult first = store.apply(command);
        ApplyCollaborationResult duplicate = store.apply(command);

        assertThat(first.newlyApplied()).isTrue();
        assertThat(duplicate.newlyApplied()).isFalse();
        assertThat(duplicate.state()).isEqualTo(first.state());
        assertThat(store.current(7L).version()).isEqualTo(1);
    }

    @Test
    void rejectsStaleVersion() {
        store.apply(command("a", 7L, CollaborationOperation.ZOOM_IN, 0));

        assertThatThrownBy(() -> store.apply(command("b", 7L, CollaborationOperation.ZOOM_IN, 0)))
                .isInstanceOf(CollaborationVersionConflictException.class);
    }

    private CollaborationCommand command(String id, long pictureId,
                                           CollaborationOperation operation, long version) {
        return new CollaborationCommand(id, pictureId, 99L, operation, version);
    }
}
