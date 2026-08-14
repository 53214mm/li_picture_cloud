package com.li.lipicturecloud.infrastructure.airuntime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.ChatTurn;
import com.li.lipicturecloud.application.airuntime.ConnectivityResult;
import com.li.lipicturecloud.application.airuntime.LanguageInvocationException;
import com.li.lipicturecloud.application.airuntime.LanguageModelInvoker;
import com.li.lipicturecloud.application.airuntime.LanguageRouteDecision;
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
 * <p>失败只抛带安全错误码的 {@link LanguageInvocationException}；日志与异常
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
    public Flux<String> stream(LanguageRouteDecision route, List<ChatTurn> turns) {
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
                    .POST(HttpRequest.BodyPublishers.ofString(encodeBody(route, turns)))
                    .build();
        } catch (IOException bodyFailure) {
            throw new LanguageInvocationException(ConnectivityResult.UPSTREAM_ERROR,
                    "failed to encode model request", bodyFailure);
        }

        return Mono.<HttpResponse<Stream<String>>>fromFuture(
                        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines()))
                .flatMapMany(response -> {
                    if (!isSuccess(response.statusCode())) {
                        response.body().close();
                        return Flux.error(statusFailure(response.statusCode()));
                    }
                    return Flux.fromStream(response.body());
                })
                .mapNotNull(this::extractDelta)
                // HttpRequest.timeout 只覆盖到响应头；对 SSE 正文叠加逐帧空闲超时，
                // 上游 200 后停滞不发帧时也能及时终止而不是无限悬挂。
                .timeout(timeout)
                .onErrorMap(this::normalizeFailure);
    }

    URI resolveEndpoint(LanguageRouteDecision route) {
        return URI.create(route.connection().endpointUri().toString()
                .replaceFirst("/+$", "") + "/chat/completions");
    }

    String encodeBody(LanguageRouteDecision route, List<ChatTurn> turns) throws IOException {
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
                + "\",\"messages\":[" + messages + "],\"stream\":true}";
    }

    String extractDelta(String line) {
        String payload = line.strip();
        if (!payload.startsWith("data:")) {
            return null;
        }
        String data = payload.substring("data:".length()).strip();
        if (data.isEmpty() || "[DONE]".equals(data)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(data);
            if (node.hasNonNull("error")) {
                throw new LanguageInvocationException(ConnectivityResult.UPSTREAM_ERROR,
                        "model endpoint reported an error");
            }
            JsonNode content = node.path("choices").path(0).path("delta").path("content");
            return content.isMissingNode() || content.isNull() || content.asText().isEmpty()
                    ? null : content.asText();
        } catch (IOException malformed) {
            throw new LanguageInvocationException(ConnectivityResult.UPSTREAM_ERROR,
                    "malformed model stream frame", malformed);
        }
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static LanguageInvocationException statusFailure(int statusCode) {
        String code = (statusCode == 401 || statusCode == 403)
                ? ConnectivityResult.CREDENTIAL_REJECTED
                : ConnectivityResult.UPSTREAM_ERROR;
        return new LanguageInvocationException(code, "model endpoint rejected the request");
    }

    private Throwable normalizeFailure(Throwable failure) {
        if (failure instanceof LanguageInvocationException) {
            return failure;
        }
        Throwable root = failure;
        if (root instanceof CompletionException && root.getCause() != null) {
            root = root.getCause();
        }
        if (root instanceof HttpTimeoutException || root instanceof java.net.SocketTimeoutException
                || root instanceof java.io.InterruptedIOException
                || root instanceof java.util.concurrent.TimeoutException) {
            return new LanguageInvocationException(ConnectivityResult.UPSTREAM_TIMEOUT,
                    "model endpoint timed out", failure);
        }
        if (root instanceof UncheckedIOException) {
            return new LanguageInvocationException(ConnectivityResult.UPSTREAM_ERROR,
                    "model endpoint transport failed", failure);
        }
        return new LanguageInvocationException(ConnectivityResult.UPSTREAM_ERROR,
                "model invocation failed", failure);
    }
}
