package com.li.lipicturecloud.collaboration;

public record CollaborationMetrics(
        long appliedCommands,
        long duplicateCommands,
        long versionConflicts,
        int activePictures
) {
}
