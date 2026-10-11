package com.li.lipicturecloud.infrastructure.airuntime;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.*;
import com.li.lipicturecloud.application.companion.presence.*;
import com.li.lipicturecloud.domain.airuntime.*;
import com.li.lipicturecloud.domain.companion.*;
import com.li.lipicturecloud.model.dto.companion.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresenceFailureDiagnosticsTest {
    HttpServer upstream;
    int status;
    String response;
    final AtomicInteger calls = new AtomicInteger();
    CompanionPresenceService service;
    ListAppender<ILoggingEvent> logs;
    Logger logger;
    ModelUsageService usage;

    static class LocalClient extends OpenAiCompatibleLanguageClient {
        final URI endpoint;
        LocalClient(URI endpoint) {
            super(new ObjectMapper(), HttpClient.newHttpClient(), Duration.ofSeconds(2));
            this.endpoint = endpoint;
        }
        @Override URI resolveEndpoint(ModelRouteDecision route) { return endpoint; }
    }

    @BeforeEach void setup() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/chat/completions", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        upstream.start();
        var router = mock(LanguageRouter.class);
        var route = ModelRouteDecision.byok(ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "fixture", URI.create("https://api.deepseek.com/v1"), "deepseek-flash", 5L, true, 0L), "fixture-only");
        when(router.decide(7)).thenReturn(route);
        var companions = mock(CompanionRepository.class);
        when(companions.findByOwnerId(7)).thenReturn(Optional.empty());
        usage = mock(ModelUsageService.class);
        service = new CompanionPresenceService(Clock.systemUTC(), router,
                new LocalClient(URI.create("http://127.0.0.1:" + upstream.getAddress().getPort() + "/chat/completions")),
                mock(PresenceQuotaGuard.class), usage, companions, mock(CompanionChatMessageRepository.class), true, true);
        logger = (Logger) LoggerFactory.getLogger(CompanionPresenceService.class);
        logs = new ListAppender<>(); logs.start(); logger.addAppender(logs);
    }
    @AfterEach void cleanup() { upstream.stop(0); logger.detachAppender(logs); logs.stop(); }

    PresenceDecisionView decide() {
        var session = service.open(7, new PresenceSessionRequest(true, "BYOK", 1, false, "NORMAL"));
        return service.decide(7, new PresenceEventRequest(session.sessionId(), "fixture-event", 1L,
                Instant.now(), "PAGE_ENTER", "COMPANION"));
    }
    String safeLogs() { return logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a,b) -> a + b); }

    @Test void rejectionStatusesAreSafeAndNoRequestIsRetried() {
        response = "{\"error\":\"fixture-secret-upstream-body\"}";
        for (var entry : Map.of(400, "MODEL_REQUEST_REJECTED", 401, "MODEL_CREDENTIAL_REJECTED",
                402, "MODEL_BUDGET", 429, "MODEL_RATE_LIMITED", 503, "MODEL_UPSTREAM_ERROR").entrySet()) {
            status = entry.getKey();
            var result = decide();
            assertThat(result.reason()).isEqualTo(entry.getValue());
            assertThat(result.action()).isEqualTo("SILENT");
            assertThat(result.remainingDecisions()).isZero();
            assertThat(safeLogs()).contains("httpStatus=" + status, "stage=COLLECT", "elapsedMs=", "outputChars=0")
                    .doesNotContain("fixture-secret-upstream-body", "fixture-only", "Authorization", "eventType=");
            assertThat(logs.list).allMatch(event -> event.getThrowableProxy() == null);
        }
        assertThat(calls.get()).isEqualTo(5);
    }

    @Test void reasoningOnlyCompletedStreamIsEmptyOutputRatherThanCredentialOrTimeoutFailure() {
        status = 200;
        response = "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"fixture-secret-reasoning\"},\"finish_reason\":\"stop\"}]}\n\n";
        assertThat(decide().reason()).isEqualTo("EMPTY_OUTPUT");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(safeLogs()).contains("stage=PARSE", "outputChars=0").doesNotContain("fixture-secret-reasoning");
    }

    @Test void invalidJsonIsKeptSeparateFromNetworkOrStreamProtocolFailure() {
        status = 200;
        response = "data: {\"choices\":[{\"delta\":{\"content\":\"fixture-secret-not-json\"},\"finish_reason\":\"stop\"}]}\n\n";
        assertThat(decide().reason()).isEqualTo("INVALID_DECISION");
        assertThat(safeLogs()).contains("stage=PARSE", "outputChars=23").doesNotContain("fixture-secret-not-json");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test void malformedSseIsProtocolFailureWithoutLoggingTheBody() {
        status = 200;
        response = "data: fixture-secret-malformed-sse\n\n";
        assertThat(decide().reason()).isEqualTo("MODEL_STREAM_INVALID");
        assertThat(safeLogs()).contains("stage=COLLECT").doesNotContain("fixture-secret-malformed-sse");
        assertThat(calls.get()).isEqualTo(1);
    }
}
