package com.li.lipicturecloud.infrastructure.companion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 百炼 OpenAI 兼容 {@code /embeddings} 的最小客户端（默认 qwen3.7-text-embedding）。
 * 只返回向量，不记录输入文本。
 */
public class DashScopeTextEmbedder {

    /** 百炼单次请求最多 10 条文本。 */
    static final int MAX_BATCH = 10;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String model;
    private final String apiKey;
    private final int dimensions;

    public DashScopeTextEmbedder(RestClient restClient, ObjectMapper objectMapper, URI endpoint,
                                 String model, String apiKey, int dimensions) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.model = Objects.requireNonNull(model, "model");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        this.dimensions = dimensions;
    }

    public static DashScopeTextEmbedder production(ObjectMapper objectMapper, URI endpoint, String model,
                                                   String apiKey, int dimensions, Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        return new DashScopeTextEmbedder(RestClient.builder().requestFactory(factory).build(), objectMapper,
                endpoint, model, apiKey, dimensions);
    }

    public int dimensions() {
        return dimensions;
    }

    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    /** Embeds texts in request batches of {@value #MAX_BATCH}, preserving order. */
    public List<float[]> embedAll(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += MAX_BATCH) {
            vectors.addAll(request(texts.subList(start, Math.min(texts.size(), start + MAX_BATCH))));
        }
        return vectors;
    }

    private List<float[]> request(List<String> batch) {
        String body = restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(Map.of("model", model, "input", batch, "dimensions", dimensions,
                        "encoding_format", "float"))
                .retrieve()
                .body(String.class);
        try {
            JsonNode data = objectMapper.readTree(body).path("data");
            float[][] ordered = new float[batch.size()][];
            for (JsonNode item : data) {
                int index = item.path("index").asInt(-1);
                JsonNode values = item.path("embedding");
                if (index < 0 || index >= batch.size() || !values.isArray() || values.size() != dimensions) {
                    throw new IllegalStateException("unexpected embedding payload");
                }
                float[] vector = new float[dimensions];
                for (int i = 0; i < dimensions; i++) {
                    vector[i] = (float) values.get(i).asDouble();
                }
                ordered[index] = vector;
            }
            for (float[] vector : ordered) {
                if (vector == null) {
                    throw new IllegalStateException("missing embedding");
                }
            }
            return List.of(ordered);
        } catch (IllegalStateException invalid) {
            throw invalid;
        } catch (Exception malformed) {
            throw new IllegalStateException("malformed embedding response", malformed);
        }
    }
}
