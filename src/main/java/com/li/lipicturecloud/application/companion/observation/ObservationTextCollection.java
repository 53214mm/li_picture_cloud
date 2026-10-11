package com.li.lipicturecloud.application.companion.observation;

import com.li.lipicturecloud.application.airuntime.LanguageStreamDiagnostics;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/** One absolute deadline, with its cause recorded before cancelling the transport. */
final class ObservationTextCollection {
    static Mono<Void> consume(Flux<String> stream, StringBuilder body, boolean[] truncated,
                              LanguageStreamDiagnostics diagnostics, Duration deadline) {
        return stream.takeUntilOther(Mono.delay(deadline).flatMap(ignored -> {
            diagnostics.ended("COLLECTION_TIMEOUT");
            return Mono.error(new TimeoutException("Observation collection deadline exceeded"));
        })).doOnNext(chunk -> {
            if (body.length() + chunk.length() > 1024) {
                body.append(chunk, 0, Math.max(0, 1024 - body.length()));
                truncated[0] = true;
                throw new IllegalArgumentException("OUTPUT_LIMIT");
            }
            body.append(chunk);
        }).then();
    }
    private ObservationTextCollection() { }
}
