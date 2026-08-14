package com.li.lipicturecloud.infrastructure.airuntime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.ConnectivityResult;
import com.li.lipicturecloud.application.airuntime.ImageGenerationResult;
import com.li.lipicturecloud.application.airuntime.ImageModelInvoker;
import com.li.lipicturecloud.application.airuntime.ModelInvocationException;
import com.li.lipicturecloud.application.airuntime.ModelRouteDecision;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * OpenAI 兼容图片生成端点客户端：POST {@code {endpoint}/images/generations}，
 * 解析 {@code data[0].url} 或 {@code data[0].b64_json}。
 *
 * <p>失败只抛带安全错误码的 {@link ModelInvocationException}；日志与异常
 * 均不携带提示词、响应正文或凭据。响应正文最多读取 1 MiB。</p>
 */
public class OpenAiCompatibleImageClient implements ImageModelInvoker {

    static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleImageClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public static OpenAiCompatibleImageClient production(ObjectMapper objectMapper,
                                                         Duration timeout) {
        Duration bounded = Objects.requireNonNull(timeout, "timeout");
        if (bounded.isZero() || bounded.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(bounded).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(bounded);
        return new OpenAiCompatibleImageClient(RestClient.builder()
                .requestFactory(requestFactory).build(), objectMapper);
    }

    @Override
    public ImageGenerationResult invoke(ModelRouteDecision route, String prompt, String size) {
        Objects.requireNonNull(route, "route");
        if (!route.isByok()) {
            throw new IllegalArgumentException("BYOK image client only handles BYOK routes");
        }
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(size, "size");
        URI endpoint = URI.create(route.connection().endpointUri().toString()
                .replaceFirst("/+$", "") + "/images/generations");
        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "model", route.connection().modelCode(),
                    "prompt", prompt,
                    "size", size,
                    "n", 1));
            String response = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + route.apiKey())
                    .body(body)
                    .exchange((request, clientResponse) -> {
                        int status = clientResponse.getStatusCode().value();
                        if (status < 200 || status >= 300) {
                            throw statusFailure(status);
                        }
                        return readResponseAtMost(clientResponse.getBody());
                    });
            return parseResult(response);
        } catch (ModelInvocationException failure) {
            throw failure;
        } catch (HttpClientErrorException | HttpServerErrorException status) {
            throw statusFailure(status.getStatusCode().value());
        } catch (ResourceAccessException transport) {
            throw new ModelInvocationException(isTimeout(transport)
                    ? ConnectivityResult.UPSTREAM_TIMEOUT : ConnectivityResult.UPSTREAM_ERROR,
                    "image endpoint transport failed", transport);
        } catch (IOException | RuntimeException conversion) {
            throw new ModelInvocationException(ConnectivityResult.UPSTREAM_ERROR,
                    "image invocation failed", conversion);
        }
    }

    private ImageGenerationResult parseResult(String rawResponse) throws IOException {
        JsonNode outer = objectMapper.readTree(rawResponse);
        JsonNode first = outer.path("data").path(0);
        if (first.isMissingNode()) {
            throw new ModelInvocationException(ConnectivityResult.UPSTREAM_ERROR,
                    "image endpoint returned no result");
        }
        String url = first.path("url").isTextual() ? first.path("url").asText() : null;
        String base64 = first.path("b64_json").isTextual() ? first.path("b64_json").asText() : null;
        if (url == null && base64 == null) {
            throw new ModelInvocationException(ConnectivityResult.UPSTREAM_ERROR,
                    "image endpoint returned an empty result");
        }
        return new ImageGenerationResult(url == null ? null : URI.create(url), base64);
    }

    private static String readResponseAtMost(InputStream input) throws IOException {
        if (input == null) {
            throw new ModelInvocationException(ConnectivityResult.UPSTREAM_ERROR,
                    "image endpoint returned no body");
        }
        try (InputStream body = input; ByteArrayOutputStream output = new ByteArrayOutputStream(4 * 1024)) {
            byte[] buffer = new byte[4 * 1024];
            int total = 0;
            while (total < MAX_RESPONSE_BYTES) {
                int read = body.read(buffer, 0, Math.min(buffer.length, MAX_RESPONSE_BYTES - total));
                if (read == -1) {
                    return output.toString(StandardCharsets.UTF_8);
                }
                output.write(buffer, 0, read);
                total += read;
            }
            if (body.read() != -1) {
                throw new ModelInvocationException(ConnectivityResult.UPSTREAM_ERROR,
                        "image endpoint response exceeded the size limit");
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private static ModelInvocationException statusFailure(int status) {
        String code = (status == 401 || status == 403)
                ? ConnectivityResult.CREDENTIAL_REJECTED
                : ConnectivityResult.UPSTREAM_ERROR;
        return new ModelInvocationException(code, "image endpoint rejected the request");
    }

    private static boolean isTimeout(Throwable failure) {
        for (Throwable current = failure; current != null && current.getCause() != current;
             current = current.getCause()) {
            if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException
                    || current instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }
}
