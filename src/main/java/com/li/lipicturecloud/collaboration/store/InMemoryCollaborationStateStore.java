package com.li.lipicturecloud.collaboration.store;

import com.li.lipicturecloud.collaboration.CollaborationVersionConflictException;
import com.li.lipicturecloud.collaboration.model.CollaborationCommand;
import com.li.lipicturecloud.collaboration.model.CollaborationState;
import com.li.lipicturecloud.domain.collaboration.CollaborationAction;
import com.li.lipicturecloud.domain.collaboration.CollaborationSession;
import com.li.lipicturecloud.domain.collaboration.CollaborationSnapshot;
import com.li.lipicturecloud.domain.collaboration.StaleCollaborationVersionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.collaboration.store", havingValue = "memory")
public class InMemoryCollaborationStateStore implements CollaborationStateStore {

    private final Map<Long, PictureSession> sessions = new ConcurrentHashMap<>();

    @Override
    public CollaborationState current(Long pictureId) {
        return sessions.computeIfAbsent(pictureId, PictureSession::new).current();
    }

    @Override
    public ApplyCollaborationResult apply(CollaborationCommand command) {
        return sessions.computeIfAbsent(command.pictureId(), PictureSession::new).apply(command);
    }

    @Override
    public int activeSessionCount() {
        return sessions.size();
    }

    private static final class PictureSession {
        private final CollaborationSession domainSession;
        private final Map<String, CollaborationState> processedCommands = new HashMap<>();

        private PictureSession(Long pictureId) {
            domainSession = CollaborationSession.start(pictureId);
        }

        private synchronized CollaborationState current() {
            return toState(domainSession.snapshot());
        }

        private synchronized ApplyCollaborationResult apply(CollaborationCommand command) {
            CollaborationState existing = processedCommands.get(command.commandId());
            if (existing != null) {
                return new ApplyCollaborationResult(existing, false);
            }
            try {
                CollaborationSnapshot snapshot = domainSession.apply(
                        CollaborationAction.valueOf(command.operation().name()), command.baseVersion());
                CollaborationState state = toState(snapshot);
                processedCommands.put(command.commandId(), state);
                return new ApplyCollaborationResult(state, true);
            } catch (StaleCollaborationVersionException exception) {
                throw new CollaborationVersionConflictException(
                        exception.expectedVersion(), exception.actualVersion());
            }
        }

        private static CollaborationState toState(CollaborationSnapshot snapshot) {
            return new CollaborationState(
                    snapshot.pictureId(), snapshot.rotation(), snapshot.scale(), snapshot.version());
        }
    }
}
