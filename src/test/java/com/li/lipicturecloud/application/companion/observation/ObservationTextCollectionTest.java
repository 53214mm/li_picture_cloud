package com.li.lipicturecloud.application.companion.observation;

import com.li.lipicturecloud.application.airuntime.LanguageStreamDiagnostics;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservationTextCollectionTest {
    @Test void lateFinishOrDoneNeverOverwritesTheFirstTerminalCause() {
        var deadline=new LanguageStreamDiagnostics();deadline.ended("COLLECTION_TIMEOUT");deadline.frame(true,false,"stop",false);
        assertThat(deadline.snapshot().terminal()).isEqualTo("COLLECTION_TIMEOUT");
        assertThat(deadline.snapshot().finishReason()).isEqualTo("stop");
        var cancel=new LanguageStreamDiagnostics();cancel.ended("CANCELLED");cancel.frame(false,false,null,true);
        assertThat(cancel.snapshot().terminal()).isEqualTo("CANCELLED");
    }
    @Test void delayedAnswerAfterReasoningPresenceCompletesInsideDocumentedProfile() {
        var diagnostics=new LanguageStreamDiagnostics();diagnostics.started(60000);diagnostics.frame(false,true,null,false);
        var body=new StringBuilder();
        ObservationTextCollection.consume(
            Flux.just("{\"action\":\"SILENT\"}").delayElements(Duration.ofSeconds(9))
                .doOnNext(value->diagnostics.frame(true,false,"stop",false)),body,new boolean[]{false},diagnostics,Duration.ofSeconds(15))
            .block(Duration.ofSeconds(17));
        assertThat(body.toString()).isEqualTo("{\"action\":\"SILENT\"}");
        assertThat(diagnostics.snapshot().reasoningPresent()).isTrue();
        assertThat(diagnostics.snapshot().terminal()).isEqualTo("FINISH_REASON");
    }
    @Test void overallDeadlineWinsBeforeUpstreamCancellationEvenWithRepeatedContent() {
        var diagnostics=new LanguageStreamDiagnostics();var body=new StringBuilder();
        assertThatThrownBy(()->ObservationTextCollection.consume(
            Flux.concat(Flux.just("prefix"),Flux.interval(Duration.ofMillis(20)).map(i->"x"))
                .doFinally(signal->diagnostics.ended("CANCELLED")),
            body,new boolean[]{false},diagnostics,Duration.ofMillis(120)).block(Duration.ofSeconds(3)))
            .hasCauseInstanceOf(TimeoutException.class);
        assertThat(diagnostics.snapshot().terminal()).isEqualTo("COLLECTION_TIMEOUT");
        assertThat(body.toString()).startsWith("prefix");
    }
    @Test void genuineCancellationAndOutputLimitRemainDistinct() {
        var diagnostics=new LanguageStreamDiagnostics();
        var subscription=ObservationTextCollection.consume(Flux.<String>never().doFinally(signal->diagnostics.ended("CANCELLED")),
            new StringBuilder(),new boolean[]{false},diagnostics,Duration.ofSeconds(15)).subscribe();
        subscription.dispose();
        assertThat(diagnostics.snapshot().terminal()).isEqualTo("CANCELLED");
        var body=new StringBuilder();boolean[] truncated={false};
        assertThatThrownBy(()->ObservationTextCollection.consume(Flux.just("x".repeat(1025)),body,truncated,
            new LanguageStreamDiagnostics(),Duration.ofSeconds(15)).block()).hasMessage("OUTPUT_LIMIT");
        assertThat(body.length()).isEqualTo(1024);assertThat(truncated[0]).isTrue();
    }
}
