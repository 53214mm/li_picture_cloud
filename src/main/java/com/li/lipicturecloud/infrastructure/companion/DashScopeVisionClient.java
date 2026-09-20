package com.li.lipicturecloud.infrastructure.companion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.li.lipicturecloud.application.companion.AuthorizedPictureContent;
import com.li.lipicturecloud.application.companion.VisualObservationCandidate;
import com.li.lipicturecloud.application.companion.VisualObservationProvider;
import com.li.lipicturecloud.application.companion.VisualObservationResult;
import com.li.lipicturecloud.application.companion.VisionProviderException;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 百炼 OpenAI 兼容 Chat Completions 的最小视觉客户端。
 *
 * <p>该类只负责受控像素到结构化候选的转换；不写伙伴、经验、权限或额度，也不记录图片、
 * HTTP 原文或供应商诊断信息。唯一允许进入成长档案的模型文字是经过严格 Schema 和长度校验的
 * {@code companionMessage}；数值仍需由后续平衡规则裁剪。</p>
 */
public final class DashScopeVisionClient implements VisualObservationProvider {

    static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final String PROMPT_VERSION = "companion-vision-v2";
    private static final String RESULT_SCHEMA_VERSION = "visual-observation-v2";
    private static final String SCHEMA_NAME = "companion_visual_observation_v2";
    private static final String PROMPT = "Analyze this one image and return only the requested JSON object. "
            + "Score each structured field from visible evidence. Write companionMessage in Simplified Chinese "
            + "as a warm first-person mini-story spoken by a curious virtual companion. Explain which visible "
            + "colors, composition, people, objects, mood, or motion cues led to the scores, while presenting "
            + "imaginative details as feelings rather than facts. Use 40 to 120 Chinese characters. "
            + "Do not include markdown, object identifiers, URLs, or copied text from the image.";
    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "mood", "sceneComplexity", "energy", "socialPresence", "motionPotential", "creativity", "confidence",
            "companionMessage");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String providerCode;
    private final String modelCode;
    private final String apiKey;

    public DashScopeVisionClient(RestClient restClient, ObjectMapper objectMapper, URI endpoint,
                                 String providerCode, String modelCode, String apiKey) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.endpoint = requireHttpsEndpoint(endpoint);
        this.providerCode = requireCode(providerCode, "providerCode");
        this.modelCode = requireCode(modelCode, "modelCode");
        this.apiKey = requireCode(apiKey, "apiKey");
    }

    /**
     * 创建有连接和读取超时的生产客户端；测试直接注入带本地 stub 的 {@link RestClient}。
     */
    public static DashScopeVisionClient fromProperties(ObjectMapper objectMapper, CompanionFeatureProperties properties) {
        Objects.requireNonNull(properties, "properties");
        Duration timeout = Objects.requireNonNull(properties.getVisionTimeout(), "visionTimeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("visionTimeout must be positive");
        }
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return new DashScopeVisionClient(RestClient.builder().requestFactory(requestFactory).build(), objectMapper,
                properties.getVisionEndpoint(), properties.getVisionProvider(), properties.getVisionModel(),
                properties.getVisionApiKey());
    }

    @Override
    public VisualObservationResult observe(AuthorizedPictureContent content, long subjectId) {
        Objects.requireNonNull(content, "content");
        try {
            String response = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(request(content))
                    .exchange((request, clientResponse) -> {
                        int status = clientResponse.getStatusCode().value();
                        if (status < 200 || status >= 300) {
                            throw statusFailure(status);
                        }
                        return readResponseAtMost(clientResponse.getBody());
                    });
            return new VisualObservationResult(parseCandidate(response), providerCode, modelCode,
                    PROMPT_VERSION, RESULT_SCHEMA_VERSION);
        } catch (VisionProviderException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw transportFailure(exception);
        } catch (RuntimeException exception) {
            // 任何未分类的 SDK/转换失败均不携带底层文本向上抛出。
            throw new VisionProviderException("VISION_UNAVAILABLE", "视觉服务暂不可用");
        }
    }

    /** 平台默认模型标识（包内使用：写使用记录真实来源）。 */
    String modelCode() {
        return modelCode;
    }

    private ObjectNode request(AuthorizedPictureContent content) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", modelCode);
        request.put("temperature", 0);
        request.put("enable_thinking", false);

        ArrayNode messages = request.putArray("messages");
        messages.addObject().put("role", "system").put("content", PROMPT);
        ArrayNode userContent = messages.addObject().put("role", "user").putArray("content");
        userContent.addObject().put("type", "text").put("text", PROMPT);
        userContent.addObject().put("type", "image_url").putObject("image_url")
                .put("url", dataUrl(content));

        ObjectNode responseFormat = request.putObject("response_format");
        responseFormat.put("type", "json_schema");
        ObjectNode jsonSchema = responseFormat.putObject("json_schema");
        jsonSchema.put("name", SCHEMA_NAME);
        jsonSchema.put("strict", true);
        ObjectNode schema = jsonSchema.putObject("schema");
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ArrayNode required = schema.putArray("required");
        REQUIRED_FIELDS.forEach(required::add);
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("mood").put("type", "string").putArray("enum")
                .add("JOYFUL").add("CALM").add("NEUTRAL").add("MELANCHOLIC").add("TENSE");
        boundedInteger(properties, "sceneComplexity");
        boundedInteger(properties, "energy");
        properties.putObject("socialPresence").put("type", "boolean");
        boundedInteger(properties, "motionPotential");
        boundedInteger(properties, "creativity");
        properties.putObject("confidence").put("type", "number").put("minimum", 0).put("maximum", 1);
        properties.putObject("companionMessage")
                .put("type", "string")
                .put("minLength", VisualObservationCandidate.MIN_MESSAGE_CODE_POINTS)
                .put("maxLength", VisualObservationCandidate.MAX_MESSAGE_CODE_POINTS);
        return request;
    }

    private static void boundedInteger(ObjectNode properties, String field) {
        properties.putObject(field).put("type", "integer").put("minimum", 0).put("maximum", 4);
    }

    private String dataUrl(AuthorizedPictureContent content) {
        return "data:" + content.mimeType() + ";base64,"
                + Base64.getEncoder().encodeToString(content.bytes());
    }

    private VisualObservationCandidate parseCandidate(String rawResponse) {
        try {
            JsonNode outer = objectMapper.readTree(rawResponse);
            JsonNode candidate = objectMapper.readTree(DashScopeVisionResponse.requiredContent(outer));
            if (candidate == null || !candidate.isObject() || !fieldNames(candidate).equals(REQUIRED_FIELDS)) {
                throw DashScopeVisionResponse.invalid();
            }
            return new VisualObservationCandidate(
                    VisualObservationCandidate.Mood.valueOf(requiredText(candidate, "mood")),
                    requiredBoundedInteger(candidate, "sceneComplexity"),
                    requiredBoundedInteger(candidate, "energy"),
                    requiredBoolean(candidate, "socialPresence"),
                    requiredBoundedInteger(candidate, "motionPotential"),
                    requiredBoundedInteger(candidate, "creativity"),
                    requiredConfidence(candidate),
                    requiredText(candidate, "companionMessage"));
        } catch (VisionProviderException exception) {
            throw exception;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw DashScopeVisionResponse.invalid();
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw DashScopeVisionResponse.invalid();
        }
        return value.textValue();
    }

    private static int requiredBoundedInteger(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isInt() || value.intValue() < 0 || value.intValue() > 4) {
            throw DashScopeVisionResponse.invalid();
        }
        return value.intValue();
    }

    private static boolean requiredBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw DashScopeVisionResponse.invalid();
        }
        return value.booleanValue();
    }

    private static java.math.BigDecimal requiredConfidence(JsonNode node) {
        JsonNode value = node.get("confidence");
        if (value == null || !value.isNumber()) {
            throw DashScopeVisionResponse.invalid();
        }
        java.math.BigDecimal confidence = value.decimalValue();
        if (confidence.compareTo(java.math.BigDecimal.ZERO) < 0 || confidence.compareTo(java.math.BigDecimal.ONE) > 0) {
            throw DashScopeVisionResponse.invalid();
        }
        return confidence;
    }

    private static String readResponseAtMost(InputStream input) throws IOException {
        if (input == null) {
            throw DashScopeVisionResponse.invalid();
        }
        try (InputStream body = input; ByteArrayOutputStream output = new ByteArrayOutputStream(4 * 1024)) {
            byte[] buffer = new byte[4 * 1024];
            int total = 0;
            while (total < MAX_RESPONSE_BYTES) {
                int read = body.read(buffer, 0, Math.min(buffer.length, MAX_RESPONSE_BYTES - total));
                if (read == -1) {
                    return output.toString(java.nio.charset.StandardCharsets.UTF_8);
                }
                output.write(buffer, 0, read);
                total += read;
            }
            if (body.read() != -1) {
                throw DashScopeVisionResponse.invalid();
            }
            return output.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static VisionProviderException statusFailure(int status) {
        String code = status == 401 || status == 403 ? "VISION_CREDENTIALS"
                : status == 429 ? "VISION_RATE_LIMITED" : "VISION_UNAVAILABLE";
        return new VisionProviderException(code, "视觉服务暂不可用");
    }

    /**
     * 传输层可能以不同包装异常报告超时；只向业务层公开稳定的安全错误码。
     */
    private static VisionProviderException transportFailure(RestClientException exception) {
        return new VisionProviderException(isTimeout(exception) ? "VISION_TIMEOUT" : "VISION_UNAVAILABLE",
                "视觉服务暂不可用");
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

    private static URI requireHttpsEndpoint(URI value) {
        Objects.requireNonNull(value, "visionEndpoint");
        if (!"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null
                || value.getRawUserInfo() != null || value.getRawFragment() != null) {
            throw new IllegalArgumentException("visionEndpoint must be an HTTPS URI");
        }
        return value;
    }

    private static String requireCode(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
