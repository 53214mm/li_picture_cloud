package com.li.lipicturecloud.infrastructure.airuntime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.ChatTurn;
import com.li.lipicturecloud.application.airuntime.ConnectivityResult;
import com.li.lipicturecloud.application.airuntime.ModelInvocationException;
import com.li.lipicturecloud.application.airuntime.LanguageModelInvoker;
import com.li.lipicturecloud.application.airuntime.ModelRouteDecision;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/**
 * OpenAI 兼容语言端点的流式客户端：POST {@code {endpoint}/chat/completions}，
 * stream=true，逐行解析 SSE {@code data:} 帧提取增量文本。
 *
 * <p>失败只抛带安全错误码的 {@link ModelInvocationException}；日志与异常
 * 均不携带提示词、响应正文或凭据。</p>
 */
public class OpenAiCompatibleLanguageClient implements LanguageModelInvoker {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration timeout;

    public OpenAiCompatibleLanguageClient(ObjectMapper objectMapper, HttpClient httpClient,
                                          Duration timeout) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public static OpenAiCompatibleLanguageClient production(ObjectMapper objectMapper,
                                                            Duration timeout) {
        return new OpenAiCompatibleLanguageClient(objectMapper,
                HttpClient.newBuilder().connectTimeout(timeout).build(), timeout);
    }

    @Override
    public Flux<String> stream(ModelRouteDecision route, List<ChatTurn> turns) {
        return streamInternal(route, turns, null);
    }

    @Override
    public Flux<String> streamBounded(ModelRouteDecision route, List<ChatTurn> turns, int maxTokens) {
        if (maxTokens < 1 || maxTokens > 256) throw new IllegalArgumentException("Invalid output token bound");
        return streamInternal(route, turns, maxTokens);
    }

    private Flux<String> streamInternal(ModelRouteDecision route, List<ChatTurn> turns, Integer maxTokens) {
        Objects.requireNonNull(route, "route");
        if (!route.isByok()) {
            throw new IllegalArgumentException("BYOK client only handles BYOK routes");
        }
        Objects.requireNonNull(turns, "turns");
        if (turns.isEmpty()) {
            throw new IllegalArgumentException("turns must not be empty");
        }

        URI endpoint = resolveEndpoint(route);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + route.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(encodeBody(route, turns, maxTokens)))
                    .build();
        } catch (IOException bodyFailure) {
            throw new ModelInvocationException(ConnectivityResult.UPSTREAM_ERROR,
                    "failed to encode model request", bodyFailure);
        }

        return Mono.<HttpResponse<Stream<String>>>fromFuture(
                        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines()))
                .flatMapMany(response -> {
                    if (!isSuccess(response.statusCode())) {
                        response.body().close();
                        return Flux.error(statusFailure(response.statusCode()));
                    }
                    // Completion is a protocol fact, not merely a closed TCP response.
                    // State belongs to this response/subscription, never to the shared client.
                    boolean[] terminal = { false };
                    return Flux.fromStream(response.body())
                            .map(this::parseFrame)
                            .takeUntil(frame -> terminal[0] = frame.terminal())
                            .<String>handle((frame, sink) -> {
                                if (frame.content() != null) sink.next(frame.content());
                            })
                            .concatWith(Flux.defer(() -> terminal[0] ? Flux.empty()
                                    : Flux.error(new ModelInvocationException(ConnectivityResult.UPSTREAM_ERROR,
                                            "model stream ended before completion"))));
                })
                // HttpRequest.timeout 只覆盖到响应头；对 SSE 正文叠加逐帧空闲超时，
                // 上游 200 后停滞不发帧时也能及时终止而不是无限悬挂。
                .timeout(timeout)
                .onErrorMap(this::normalizeFailure);
    }

    URI resolveEndpoint(ModelRouteDecision route) {
        return URI.create(route.connection().endpointUri().toString()
                .replaceFirst("/+$", "") + "/chat/completions");
    }

    String encodeBody(ModelRouteDecision route, List<ChatTurn> turns) throws IOException {
        return encodeBody(route, turns, null);
    }

    String encodeBody(ModelRouteDecision route, List<ChatTurn> turns, Integer maxTokens) throws IOException {
        StringBuilder messages = new StringBuilder();
        for (ChatTurn turn : turns) {
            if (messages.length() > 0) {
                messages.append(',');
            }
            messages.append("{\"role\":\"")
                    .append(turn.role())
                    .append("\",\"content\":")
                    .append(objectMapper.writeValueAsString(turn.content()))
                    .append('}');
        }
        return "{\"model\":\"" + route.connection().modelCode()
                + "\",\"messages\":[" + messages + "],\"stream\":true"
                + (maxTokens == null ? "" : ",\"max_tokens\":" + maxTokens) + "}";
    }

    String extractDelta(String line) {
        return parseFrame(line).content();
    }

    private record StreamFrame(String content, boolean terminal) { }

    private StreamFrame parseFrame(String line) {
        String payload = line.strip();
        if (!payload.startsWith("data:")) {
            return new StreamFrame(null, false);
        }
        String data = payload.substring("data:".length()).strip();
        if ("[DONE]".equals(data)) return new StreamFrame(null, true);
        if (data.isEmpty()) return new StreamFrame(null, false);
        try {
            JsonNode node = objectMapper.readTree(data);
            if (node.hasNonNull("error")) {
                throw new ModelInvocationException(ConnectivityResult.UPSTREAM_ERROR,
                        "model endpoint reported an error");
            }
            JsonNode choice = node.path("choices").path(0);
            JsonNode content = choice.path("delta").path("content");
            JsonNode finish = choice.path("finish_reason");
            // Providers may close after finish_reason without a separate [DONE].
            // A token-limit finish (length) is still an explicit provider completion.
            boolean terminal = finish.isTextual() && !finish.asText().isBlank();
            return new StreamFrame(content.isMissingNode() || content.isNull() || content.asText().isEmpty()
                    ? null : content.asText(), terminal);
        } catch (IOException malformed) {
            throw new ModelInvocationException(ConnectivityResult.UPSTREAM_ERROR,
                    "malformed model stream frame", malformed);
        }
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static ModelInvocationException statusFailure(int statusCode) {
        String code = (statusCode == 401 || statusCode == 403)
                ? ConnectivityResult.CREDENTIAL_REJECTED
                : ConnectivityResult.UPSTREAM_ERROR;
        return new ModelInvocationException(code, "model endpoint rejected the request");
    }

    private Throwable normalizeFailure(Throwable failure) {
        if (failure instanceof ModelInvocationException) {
            return failure;
        }
        Throwable root = failure;
        if (root instanceof CompletionException && root.getCause() != null) {
            root = root.getCause();
        }
        if (root instanceof HttpTimeoutException || root instanceof java.net.SocketTimeoutException
                || root instanceof java.io.InterruptedIOException
                || root instanceof java.util.concurrent.TimeoutException) {
            return new ModelInvocationException(ConnectivityResult.UPSTREAM_TIMEOUT,
                    "model endpoint timed out", failure);
        }
        if (root instanceof UncheckedIOException) {
            return new ModelInvocationException(ConnectivityResult.UPSTREAM_ERROR,
                    "model endpoint transport failed", failure);
        }
        return new ModelInvocationException(ConnectivityResult.UPSTREAM_ERROR,
                "model invocation failed", failure);
    }
}
