package com.li.lipicturecloud.infrastructure.airuntime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.ChatTurn;
import com.li.lipicturecloud.application.airuntime.ConnectivityResult;
import com.li.lipicturecloud.application.airuntime.LanguageInvocationException;
import com.li.lipicturecloud.application.airuntime.LanguageRouteDecision;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleLanguageClientTest {

    private HttpServer stub;
    private int port;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private TestableClient client;
    private LanguageRouteDecision route;

    /** 覆盖端点解析指向本地 stub；其余行为与生产客户端一致。 */
    static class TestableClient extends OpenAiCompatibleLanguageClient {
        URI endpointOverride;

        TestableClient(ObjectMapper objectMapper, HttpClient httpClient, Duration timeout) {
            super(objectMapper, httpClient, timeout);
        }

        @Override
        URI resolveEndpoint(LanguageRouteDecision route) {
            return endpointOverride;
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = stub.getAddress().getPort();
        stub.start();
        client = new TestableClient(new ObjectMapper(), HttpClient.newHttpClient(),
                Duration.ofSeconds(5));
        client.endpointOverride = URI.create("http://127.0.0.1:" + port + "/chat/completions");
        route = LanguageRouteDecision.byok(ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "主力", URI.create("https://api.deepseek.com/v1"), "deepseek-chat", 5L, true, 1L),
                "sk-test-key");
    }

    @AfterEach
    void tearDown() {
        stub.stop(0);
    }

    private void respond(int status, String body) {
        stub.createContext("/chat/completions", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
    }

    @Test
    void streamsDeltasAcrossSseFramesUntilDone() {
        respond(200, """
                data: {"choices":[{"delta":{"content":"你"}}]}

                : keep-alive

                data: {"choices":[{"delta":{"content":"好"}}]}

                data: {"choices":[{"delta":{}}]}

                data: [DONE]
                """);

        Flux<String> stream = client.stream(route, List.of(ChatTurn.user("在吗")));

        assertThat(stream.collectList().block()).containsExactly("你", "好");
        assertThat(lastAuthorization.get()).isEqualTo("Bearer sk-test-key");
        String body = lastBody.get();
        assertThat(body).contains("\"model\":\"deepseek-chat\"");
        assertThat(body).contains("\"stream\":true");
        assertThat(body).contains("\"role\":\"user\"");
        assertThat(body).contains("在吗");
        assertThat(body).doesNotContain("sk-test-key");
    }

    @Test
    void mapsUnauthorizedToCredentialRejected() {
        respond(401, "{\"error\":\"invalid api key\"}");

        Flux<String> stream = client.stream(route, List.of(ChatTurn.user("在吗")));

        assertThatThrownBy(stream.collectList()::block)
                .isInstanceOf(LanguageInvocationException.class)
                .extracting(invocation -> ((LanguageInvocationException) invocation).safeErrorCode())
                .isEqualTo(ConnectivityResult.CREDENTIAL_REJECTED);
    }

    @Test
    void mapsServerErrorToUpstreamError() {
        respond(500, "boom");

        Flux<String> stream = client.stream(route, List.of(ChatTurn.user("在吗")));

        assertThatThrownBy(stream.collectList()::block)
                .isInstanceOf(LanguageInvocationException.class)
                .extracting(invocation -> ((LanguageInvocationException) invocation).safeErrorCode())
                .isEqualTo(ConnectivityResult.UPSTREAM_ERROR);
    }

    @Test
    void mapsErrorFrameToUpstreamError() {
        respond(200, """
                data: {"choices":[],"error":{"message":"upstream exploded"}}

                """);

        Flux<String> stream = client.stream(route, List.of(ChatTurn.user("在吗")));

        assertThatThrownBy(stream.collectList()::block)
                .isInstanceOf(LanguageInvocationException.class)
                .extracting(invocation -> ((LanguageInvocationException) invocation).safeErrorCode())
                .isEqualTo(ConnectivityResult.UPSTREAM_ERROR);
    }

    @Test
    void extractDeltaParsesContentAndRejectsMalformedFrames() {
        assertThat(client.extractDelta("data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}"))
                .isEqualTo("好");
        assertThat(client.extractDelta("data: [DONE]")).isNull();
        assertThat(client.extractDelta(": comment")).isNull();
        assertThat(client.extractDelta("data: {\"choices\":[{\"delta\":{}}]}")).isNull();
        assertThatThrownBy(() -> client.extractDelta("data: not-json"))
                .isInstanceOf(LanguageInvocationException.class)
                .extracting(invocation -> ((LanguageInvocationException) invocation).safeErrorCode())
                .isEqualTo(ConnectivityResult.UPSTREAM_ERROR);
        assertThatThrownBy(() -> client.extractDelta(
                "data: {\"error\":{\"message\":\"bad\"}}"))
                .isInstanceOf(LanguageInvocationException.class);
    }

    @Test
    void rejectsPlatformRoutesAndEmptyTurns() {
        assertThatThrownBy(() -> client.stream(LanguageRouteDecision.platform(),
                List.of(ChatTurn.user("在吗")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.stream(route, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
