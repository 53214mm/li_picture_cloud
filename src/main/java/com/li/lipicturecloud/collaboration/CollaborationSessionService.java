package com.li.lipicturecloud.collaboration;

import com.li.lipicturecloud.collaboration.model.CollaborationCommand;
import com.li.lipicturecloud.collaboration.model.CollaborationState;
import com.li.lipicturecloud.collaboration.store.ApplyCollaborationResult;
import com.li.lipicturecloud.collaboration.store.CollaborationStateStore;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.LongAdder;

@Service
public class CollaborationSessionService {

    private final CollaborationStateStore stateStore;
    private final LongAdder appliedCommands = new LongAdder();
    private final LongAdder duplicateCommands = new LongAdder();
    private final LongAdder versionConflicts = new LongAdder();

    public CollaborationSessionService(CollaborationStateStore stateStore) {
        this.stateStore = stateStore;
    }

    public CollaborationState current(Long pictureId) {
        validatePictureId(pictureId);
        return stateStore.current(pictureId);
    }

    public CollaborationState apply(CollaborationCommand command) {
        validate(command);
        try {
            ApplyCollaborationResult result = stateStore.apply(command);
            if (result.newlyApplied()) {
                appliedCommands.increment();
            } else {
                duplicateCommands.increment();
            }
            return result.state();
        } catch (CollaborationVersionConflictException exception) {
            versionConflicts.increment();
            throw exception;
        }
    }

    public CollaborationMetrics metrics() {
        return new CollaborationMetrics(
                appliedCommands.sum(), duplicateCommands.sum(), versionConflicts.sum(),
                stateStore.activeSessionCount());
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
}
