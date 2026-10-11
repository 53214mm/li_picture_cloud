package com.li.lipicturecloud.infrastructure.airuntime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.*;
import com.li.lipicturecloud.domain.airuntime.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PresenceBoundedLanguageClientTest {
    @Test void boundedEncodingSetsTokenCapWithoutChangingOrdinaryChat() throws Exception {
        var client = new OpenAiCompatibleLanguageClient(new ObjectMapper(),HttpClient.newHttpClient(),Duration.ofSeconds(1));
        var route = ModelRouteDecision.byok(ModelConnection.restore(9L,7L,ModelProvider.DEEPSEEK,"fixture",
                URI.create("https://api.deepseek.com/v1"),"deepseek-chat",5L,true,0L),"fixture");
        var turns = List.of(ChatTurn.user("eventType=PAGE_ENTER"));
        var json = new ObjectMapper().readTree(client.encodeBody(route,turns,160));
        assertThat(json.path("max_tokens").intValue()).isEqualTo(160);
        assertThat(json.has("tools")).isFalse();
        assertThat(client.encodeBody(route,turns)).doesNotContain("max_tokens");
        assertThatThrownBy(() -> client.streamBounded(route,turns,0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.streamBounded(route,turns,257)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unsupportedAdaptersFailClosedWithoutCallingTheirUnboundedPath() {
        LanguageModelInvoker legacy = (route, turns) -> { throw new AssertionError("must not call unbounded stream"); };
        assertThatThrownBy(() -> legacy.streamBounded(null,List.of(),160).blockLast())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
