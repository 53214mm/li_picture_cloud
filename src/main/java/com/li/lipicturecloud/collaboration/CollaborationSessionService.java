package com.li.lipicturecloud.collaboration;

import com.li.lipicturecloud.collaboration.model.CollaborationCommand;
import com.li.lipicturecloud.collaboration.model.CollaborationState;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CollaborationSessionService {

    private static final double MIN_SCALE = 0.25;
    private static final double MAX_SCALE = 4.0;
    private static final double SCALE_STEP = 0.1;

    private final Map<Long, PictureSession> sessions = new ConcurrentHashMap<>();

    public CollaborationState current(Long pictureId) {
        validatePictureId(pictureId);
        return sessions.computeIfAbsent(pictureId, PictureSession::new).current();
    }

    public CollaborationState apply(CollaborationCommand command) {
        validate(command);
        return sessions.computeIfAbsent(command.pictureId(), PictureSession::new).apply(command);
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

    private static final class PictureSession {
        private CollaborationState state;
        private final Map<String, CollaborationState> processedCommands = new HashMap<>();

        private PictureSession(Long pictureId) {
            state = CollaborationState.initial(pictureId);
        }

        private synchronized CollaborationState current() {
            return state;
        }

        private synchronized CollaborationState apply(CollaborationCommand command) {
            CollaborationState existing = processedCommands.get(command.commandId());
            if (existing != null) {
                return existing;
            }
            if (command.baseVersion() != state.version()) {
                throw new CollaborationVersionConflictException(command.baseVersion(), state.version());
            }

            int rotation = state.rotation();
            double scale = state.scale();
            switch (command.operation()) {
                case ROTATE_LEFT -> rotation = Math.floorMod(rotation - 90, 360);
                case ROTATE_RIGHT -> rotation = Math.floorMod(rotation + 90, 360);
                case ZOOM_IN -> scale = Math.min(MAX_SCALE, round(scale + SCALE_STEP));
                case ZOOM_OUT -> scale = Math.max(MIN_SCALE, round(scale - SCALE_STEP));
            }
            state = new CollaborationState(state.pictureId(), rotation, scale, state.version() + 1);
            processedCommands.put(command.commandId(), state);
            return state;
        }

        private double round(double value) {
            return Math.round(value * 100.0) / 100.0;
        }
    }
}
