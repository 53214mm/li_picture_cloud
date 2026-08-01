package com.li.lipicturecloud.collaboration;

import com.li.lipicturecloud.collaboration.model.CollaborationCommand;
import com.li.lipicturecloud.collaboration.model.CollaborationOperation;
import com.li.lipicturecloud.collaboration.store.InMemoryCollaborationStateStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CollaborationThroughputBenchmarkTest {

    @Test
    void measuresDirectStateMachineThroughput() {
        CollaborationSessionService service = new CollaborationSessionService(
                new InMemoryCollaborationStateStore());
        int commands = 100_000;
        long started = System.nanoTime();
        for (int i = 0; i < commands; i++) {
            service.apply(new CollaborationCommand(
                    "benchmark-" + i, 1L, 1L, CollaborationOperation.ROTATE_RIGHT, i));
        }
        long elapsedNanos = System.nanoTime() - started;
        long operationsPerSecond = Math.round(commands * 1_000_000_000.0 / elapsedNanos);

        System.out.printf("COLLABORATION_BENCHMARK commands=%d elapsedMs=%.2f opsPerSecond=%d%n",
                commands, elapsedNanos / 1_000_000.0, operationsPerSecond);
        assertThat(service.current(1L).version()).isEqualTo(commands);
    }
}
