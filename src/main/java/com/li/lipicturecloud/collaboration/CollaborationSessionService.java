package com.li.lipicturecloud.collaboration;

import com.li.lipicturecloud.collaboration.model.CollaborationCommand;
import com.li.lipicturecloud.collaboration.model.CollaborationState;
import com.li.lipicturecloud.domain.collaboration.CollaborationAction;
import com.li.lipicturecloud.domain.collaboration.CollaborationSession;
import com.li.lipicturecloud.domain.collaboration.CollaborationSnapshot;
import com.li.lipicturecloud.domain.collaboration.StaleCollaborationVersionException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Service
public class CollaborationSessionService {

    private final Map<Long, PictureSession> sessions = new ConcurrentHashMap<>();
    private final LongAdder appliedCommands = new LongAdder();
    private final LongAdder duplicateCommands = new LongAdder();
    private final LongAdder versionConflicts = new LongAdder();

    public CollaborationState current(Long pictureId) {
        validatePictureId(pictureId);
        return sessions.computeIfAbsent(pictureId, PictureSession::new).current();
    }

    public CollaborationState apply(CollaborationCommand command) {
        validate(command);
        return sessions.computeIfAbsent(command.pictureId(), PictureSession::new).apply(command);
    }

    public CollaborationMetrics metrics() {
        return new CollaborationMetrics(
                appliedCommands.sum(), duplicateCommands.sum(), versionConflicts.sum(), sessions.size());
    }

    private void validate(CollaborationCommand command) {
        if (command == null || command.commandId() == null || command.commandId().isBlank()
                || command.operation() == null || command.actorUserId() == null) {
            throw new IllegalArgumentException("协同命令字段不完整");
        }
        validatePictureId(command.pictureId());
        if (command.baseVersion() < 0) {
            throw new IllegalArgumentException("协同版本不能为负数");
        }
    }

    private void validatePictureId(Long pictureId) {
        if (pictureId == null || pictureId <= 0) {
            throw new IllegalArgumentException("图片 ID 无效");
        }
    }

    private final class PictureSession {
        private final CollaborationSession domainSession;
        private final Map<String, CollaborationState> processedCommands = new HashMap<>();

        private PictureSession(Long pictureId) {
            domainSession = CollaborationSession.start(pictureId);
        }

        private synchronized CollaborationState current() {
            return toState(domainSession.snapshot());
        }

        private synchronized CollaborationState apply(CollaborationCommand command) {
            CollaborationState existing = processedCommands.get(command.commandId());
            if (existing != null) {
                duplicateCommands.increment();
                return existing;
            }
            CollaborationState state;
            try {
                CollaborationSnapshot snapshot = domainSession.apply(
                        CollaborationAction.valueOf(command.operation().name()), command.baseVersion());
                state = toState(snapshot);
            } catch (StaleCollaborationVersionException exception) {
                versionConflicts.increment();
                throw new CollaborationVersionConflictException(
                        exception.expectedVersion(), exception.actualVersion());
            }
            processedCommands.put(command.commandId(), state);
            appliedCommands.increment();
            return state;
        }

        private CollaborationState toState(CollaborationSnapshot snapshot) {
            return new CollaborationState(
                    snapshot.pictureId(), snapshot.rotation(), snapshot.scale(), snapshot.version());
        }
    }
}
