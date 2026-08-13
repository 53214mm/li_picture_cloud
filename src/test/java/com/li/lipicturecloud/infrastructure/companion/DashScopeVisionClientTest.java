package com.li.lipicturecloud.infrastructure.companion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.companion.AuthorizedPictureContent;
import com.li.lipicturecloud.application.companion.VisualObservationCandidate;
import com.li.lipicturecloud.application.companion.VisionProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

/**
 * 通过本地 HTTP stub 固定供应商请求契约，测试从不向 DashScope 发起真实调用。
 */
class DashScopeVisionClientTest {

    private static final URI ENDPOINT = URI.create("https://dashscope.test/compatible-mode/v1/chat/completions");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private DashScopeVisionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DashScopeVisionClient(builder.build(), objectMapper, ENDPOINT,
                "dashscope", "qwen3.6-flash", "test-api-key");
    }

    @Test
    void sendsStrictStructuredVisionRequestAndParsesTheCandidate() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("qwen3.6-flash"))
                .andExpect(jsonPath("$.temperature").value(0))
                .andExpect(jsonPath("$.enable_thinking").value(false))
                .andExpect(jsonPath("$.messages[1].content[1].image_url.url")
                        .value("data:image/jpeg;base64,/9j/"))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.schema.additionalProperties").value(false))
                .andRespond(withSuccess(validResponse(), MediaType.APPLICATION_JSON));

        VisualObservationCandidate candidate = client.observe(jpegContent());

        assertThat(candidate).isEqualTo(new VisualObservationCandidate(
                VisualObservationCandidate.Mood.JOYFUL, 2, 3, true, 2, 3, new BigDecimal("0.84")));
        assertThat(client.providerCode()).isEqualTo("dashscope");
        assertThat(client.modelCode()).isEqualTo("qwen3.6-flash");
        assertThat(client.promptVersion()).isEqualTo("companion-vision-v1");
        assertThat(client.resultSchemaVersion()).isEqualTo("visual-observation-v1");
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("providerFailures")
    void mapsProviderStatusWithoutExposingResponseBody(HttpStatus status, String expectedCode) {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(status).contentType(MediaType.TEXT_PLAIN).body("provider internals"));

        assertThatThrownBy(() -> client.observe(jpegContent()))
                .isInstanceOf(VisionProviderException.class)
                .hasMessage("视觉服务暂不可用")
                .extracting(error -> ((VisionProviderException) error).safeCode())
                .isEqualTo(expectedCode);
    }

    @Test
    void rejectsMarkdownOrSchemaBreakingContentInsteadOfGuessing() {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(responseContent("""
                ```json
                {"mood":"JOYFUL"}
                ```
                """), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.observe(jpegContent()))
                .isInstanceOf(VisionProviderException.class)
                .extracting(error -> ((VisionProviderException) error).safeCode())
                .isEqualTo("VISION_INVALID_RESPONSE");
    }

    @ParameterizedTest
    @MethodSource("invalidModelPayloads")
    void rejectsMissingExtraOrOutOfRangeStructuredFields(String rawResponse) {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(rawResponse, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.observe(jpegContent()))
                .isInstanceOf(VisionProviderException.class)
                .extracting(error -> ((VisionProviderException) error).safeCode())
                .isEqualTo("VISION_INVALID_RESPONSE");
    }

    @Test
    void mapsTransportTimeoutToStableSafeCode() {
        server.expect(requestTo(ENDPOINT)).andRespond(withException(new SocketTimeoutException("internal timeout")));

        assertThatThrownBy(() -> client.observe(jpegContent()))
                .isInstanceOf(VisionProviderException.class)
                .hasMessage("视觉服务暂不可用")
                .extracting(error -> ((VisionProviderException) error).safeCode())
                .isEqualTo("VISION_TIMEOUT");
    }

    @Test
    void rejectsAResponseLargerThanTheFixed64KiBLimit() {
        String oversized = "x".repeat(65 * 1024);
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(oversized, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.observe(jpegContent()))
                .isInstanceOf(VisionProviderException.class)
                .extracting(error -> ((VisionProviderException) error).safeCode())
                .isEqualTo("VISION_INVALID_RESPONSE");
    }

    private static Stream<Arguments> providerFailures() {
        return Stream.of(
                Arguments.of(HttpStatus.UNAUTHORIZED, "VISION_CREDENTIALS"),
                Arguments.of(HttpStatus.FORBIDDEN, "VISION_CREDENTIALS"),
                Arguments.of(HttpStatus.TOO_MANY_REQUESTS, "VISION_RATE_LIMITED"),
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, "VISION_UNAVAILABLE"));
    }

    private static Stream<Arguments> invalidModelPayloads() {
        return Stream.of(
                Arguments.of("{\"choices\":[]}"),
                Arguments.of(responseContent("{\"mood\":\"JOYFUL\"}")),
                Arguments.of(responseContent(validCandidateWithSuffix(",\"unexpected\":true"))),
                Arguments.of(responseContent(validCandidateWithReplacement("\"energy\":3", "\"energy\":3.0"))),
                Arguments.of(responseContent(validCandidateWithReplacement("\"confidence\":0.84", "\"confidence\":1.01"))));
    }

    private static AuthorizedPictureContent jpegContent() {
        return new AuthorizedPictureContent(102L, Instant.parse("2026-08-13T12:00:00Z"),
                "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }

    private static String validResponse() {
        return responseContent(validCandidate());
    }

    private static String validCandidate() {
        return """
                {"mood":"JOYFUL","sceneComplexity":2,"energy":3,"socialPresence":true,
                "motionPotential":2,"creativity":3,"confidence":0.84}
                """;
    }

    private static String validCandidateWithSuffix(String suffix) {
        String candidate = validCandidate().strip();
        return candidate.substring(0, candidate.length() - 1) + suffix + "}";
    }

    private static String validCandidateWithReplacement(String original, String replacement) {
        return validCandidate().replace(original, replacement);
    }

    private static String responseContent(String content) {
        try {
            return new ObjectMapper().writeValueAsString(java.util.Map.of(
                    "choices", java.util.List.of(java.util.Map.of(
                            "message", java.util.Map.of("content", content)))));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
