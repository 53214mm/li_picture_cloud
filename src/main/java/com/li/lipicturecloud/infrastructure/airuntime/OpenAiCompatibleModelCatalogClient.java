package com.li.lipicturecloud.infrastructure.airuntime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.ConnectivityResult;
import com.li.lipicturecloud.application.airuntime.ModelCatalogClient;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

/**
 * OpenAI 兼容端点的模型目录与最小对话验证。
 *
 * <p>目录读取 {@code GET {endpoint}/models}，最小对话 {@code POST {endpoint}/chat/completions}
 * （1 个输出 token）。只看状态码与模型 id，不记录、不返回响应正文。</p>
 */
public class OpenAiCompatibleModelCatalogClient implements ModelCatalogClient {

    /** 明显不能用于对话的模型类别（嵌入、语音、绘图、审核等），只用于收窄列表，不代表能力判断。 */
    private static final List<String> NON_CHAT_MARKERS = List.of("embed", "whisper", "tts", "dall-e",
            "moderation", "rerank", "transcri", "speech", "audio", "-image", "image-", "sora",
            "stable-diffusion", "flux", "kolors", "realtime", "bge-");
    private static final int MAX_MODELS = 200;
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleModelCatalogClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /** 生产实例：JDK HttpClient，连接与读取共享同一超时预算。 */
    public static OpenAiCompatibleModelCatalogClient production(ObjectMapper objectMapper, Duration timeout) {
        Duration bounded = Objects.requireNonNull(timeout, "timeout");
        if (bounded.isZero() || bounded.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(bounded).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(bounded);
        return new OpenAiCompatibleModelCatalogClient(RestClient.builder()
                .requestFactory(requestFactory).build(), objectMapper);
    }

    @Override
    public Catalog listModels(URI endpoint, String apiKey, ModelProvider provider) {
        Objects.requireNonNull(provider, "provider");
        try {
            String body = restClient.get()
                    .uri(resolve(endpoint, "/models"))
                    .headers(authorization(apiKey, provider))
                    .retrieve()
                    .body(String.class);
            List<String> models = parseModels(body);
            return models.isEmpty() ? Catalog.unlisted() : Catalog.of(models);
        } catch (HttpClientErrorException rejected) {
            int status = rejected.getStatusCode().value();
            if (status == 404 || status == 405) {
                // 供应商没有开放目录接口：凭据尚未验证，交给最小对话验证。
                return Catalog.unlisted();
            }
            return Catalog.failed(clientFailure(rejected.getStatusCode()));
        } catch (HttpServerErrorException serverError) {
            return Catalog.failed(ConnectivityResult.UPSTREAM_ERROR);
        } catch (ResourceAccessException transport) {
            return Catalog.failed(transportFailure(transport));
        }
    }

    @Override
    public ConnectivityResult probeChat(URI endpoint, String apiKey, ModelProvider provider,
                                        String modelCode) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(modelCode, "modelCode");
        try {
            return send(endpoint, apiKey, provider, modelCode, "max_tokens", 1);
        } catch (HttpClientErrorException rejected) {
            if (rejected.getStatusCode().value() != 400) {
                return ConnectivityResult.failed(chatFailure(rejected.getStatusCode()));
            }
            // 部分推理模型拒绝 max_tokens，只接受 max_completion_tokens：换参数再试一次。
            try {
                return send(endpoint, apiKey, provider, modelCode, "max_completion_tokens", 16);
            } catch (HttpClientErrorException retryRejected) {
                return ConnectivityResult.failed(chatFailure(retryRejected.getStatusCode()));
            } catch (HttpServerErrorException serverError) {
                return ConnectivityResult.failed(ConnectivityResult.UPSTREAM_ERROR);
            } catch (ResourceAccessException transport) {
                return ConnectivityResult.failed(transportFailure(transport));
            }
        } catch (HttpServerErrorException serverError) {
            return ConnectivityResult.failed(ConnectivityResult.UPSTREAM_ERROR);
        } catch (ResourceAccessException transport) {
            return ConnectivityResult.failed(transportFailure(transport));
        }
    }

    private ConnectivityResult send(URI endpoint, String apiKey, ModelProvider provider,
                                    String modelCode, String limitField, int limit) {
        Map<String, Object> payload = Map.of(
                "model", modelCode,
                "messages", List.of(Map.of("role", "user", "content", "ping")),
                limitField, limit,
                "stream", false);
        restClient.post()
                .uri(resolve(endpoint, "/chat/completions"))
                .headers(authorization(apiKey, provider))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        return ConnectivityResult.success();
    }

    /** 解析 {@code {"data":[{"id":...}]}}；Gemini 兼容端点的 id 带 {@code models/} 前缀，去掉后才是可调用编码。 */
    List<String> parseModels(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonNode data;
        try {
            data = objectMapper.readTree(body).path("data");
        } catch (IOException malformed) {
            return List.of();
        }
        if (!data.isArray()) {
            return List.of();
        }
        TreeSet<String> models = new TreeSet<>();
        StreamSupport.stream(data.spliterator(), false)
                .map(item -> item.path("id").asText(""))
                .map(id -> id.startsWith("models/") ? id.substring("models/".length()) : id)
                .filter(id -> ModelConnection.MODEL_CODE.matcher(id).matches())
                .filter(OpenAiCompatibleModelCatalogClient::looksConversational)
                .forEach(models::add);
        return models.stream().limit(MAX_MODELS).toList();
    }

    private static boolean looksConversational(String modelCode) {
        String lower = modelCode.toLowerCase(Locale.ROOT);
        return NON_CHAT_MARKERS.stream().noneMatch(lower::contains);
    }

    private static Consumer<HttpHeaders> authorization(String apiKey, ModelProvider provider) {
        Objects.requireNonNull(apiKey, "apiKey");
        return headers -> {
            headers.setBearerAuth(apiKey);
            if (provider == ModelProvider.ANTHROPIC) {
                // Anthropic 原生目录接口用 x-api-key；兼容层的对话接口接受 Bearer。
                headers.set("x-api-key", apiKey);
                headers.set("anthropic-version", ANTHROPIC_VERSION);
            }
        };
    }

    private static URI resolve(URI endpoint, String path) {
        Objects.requireNonNull(endpoint, "endpoint");
        return URI.create(endpoint.toString().replaceFirst("/+$", "") + path);
    }

    private static String clientFailure(HttpStatusCode status) {
        int code = status.value();
        if (code == 401 || code == 403) {
            return ConnectivityResult.CREDENTIAL_REJECTED;
        }
        if (code == 402 || code == 429) {
            return ConnectivityResult.QUOTA_EXHAUSTED;
        }
        return ConnectivityResult.UPSTREAM_ERROR;
    }

    private static String chatFailure(HttpStatusCode status) {
        int code = status.value();
        if (code == 400 || code == 404 || code == 422) {
            return ConnectivityResult.MODEL_UNAVAILABLE;
        }
        return clientFailure(status);
    }

    private static String transportFailure(ResourceAccessException transport) {
        Throwable cause = transport;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException
                    || cause instanceof InterruptedIOException) {
                return ConnectivityResult.UPSTREAM_TIMEOUT;
            }
            cause = cause.getCause();
        }
        return ConnectivityResult.UPSTREAM_ERROR;
    }
}
