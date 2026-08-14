package com.li.lipicturecloud.infrastructure.airuntime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.ConnectivityResult;
import com.li.lipicturecloud.application.airuntime.ImageGenerationResult;
import com.li.lipicturecloud.application.airuntime.ModelInvocationException;
import com.li.lipicturecloud.application.airuntime.ModelRouteDecision;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

/**
 * 通过本地 HTTP stub 固定 OpenAI 兼容图片生成契约，测试从不发起真实调用。
 */
class OpenAiCompatibleImageClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private OpenAiCompatibleImageClient client;
    private ModelRouteDecision route;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiCompatibleImageClient(builder.build(), objectMapper);
        route = ModelRouteDecision.byok(ModelConnection.restore(9L, 7L, ModelProvider.OPENAI,
                "出图主力", URI.create("https://api.openai.com/v1"), "gpt-image-2", 5L, true, 1L),
                "sk-openai");
    }

    @Test
    void postsPromptAndParsesReturnedUrl() {
        server.expect(requestTo("https://api.openai.com/v1/images/generations"))
                .andExpect(header("Authorization", "Bearer sk-openai"))
                .andExpect(jsonPath("$.model").value("gpt-image-2"))
                .andExpect(jsonPath("$.prompt").value("一只安静的猫"))
                .andExpect(jsonPath("$.size").value("1024x1024"))
                .andExpect(jsonPath("$.n").value(1))
                .andRespond(withSuccess("""
                        {"data":[{"url":"https://cdn.example.test/result.png"}]}
                        """, MediaType.APPLICATION_JSON));

        ImageGenerationResult result = client.invoke(route, "一只安静的猫", "1024x1024");

        assertThat(result.imageUrl()).isEqualTo(URI.create("https://cdn.example.test/result.png"));
        assertThat(result.base64Image()).isNull();
        server.verify();
    }

    @Test
    void parsesInlineBase64Results() {
        server.expect(requestTo("https://api.openai.com/v1/images/generations"))
                .andRespond(withSuccess("""
                        {"data":[{"b64_json":"aGVsbG8="}]}
                        """, MediaType.APPLICATION_JSON));

        ImageGenerationResult result = client.invoke(route, "一只安静的猫", "1024x1024");

        assertThat(result.imageUrl()).isNull();
        assertThat(result.base64Image()).isEqualTo("aGVsbG8=");
        server.verify();
    }

    @Test
    void mapsRejectedCredentialsAndServerFailuresToSafeCodes() {
        server.expect(requestTo("https://api.openai.com/v1/images/generations"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo("https://api.openai.com/v1/images/generations"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.invoke(route, "一只安静的猫", "1024x1024"))
                .isInstanceOf(ModelInvocationException.class)
                .extracting(error -> ((ModelInvocationException) error).safeErrorCode())
                .isEqualTo(ConnectivityResult.CREDENTIAL_REJECTED);
        assertThatThrownBy(() -> client.invoke(route, "一只安静的猫", "1024x1024"))
                .isInstanceOf(ModelInvocationException.class)
                .extracting(error -> ((ModelInvocationException) error).safeErrorCode())
                .isEqualTo(ConnectivityResult.UPSTREAM_ERROR);
        server.verify();
    }

    @Test
    void mapsTransportTimeoutToStableSafeCode() {
        server.expect(requestTo("https://api.openai.com/v1/images/generations"))
                .andRespond(withException(new SocketTimeoutException("internal timeout")));

        assertThatThrownBy(() -> client.invoke(route, "一只安静的猫", "1024x1024"))
                .isInstanceOf(ModelInvocationException.class)
                .extracting(error -> ((ModelInvocationException) error).safeErrorCode())
                .isEqualTo(ConnectivityResult.UPSTREAM_TIMEOUT);
        server.verify();
    }

    @Test
    void rejectsEmptyOrMalformedResponses() {
        server.expect(requestTo("https://api.openai.com/v1/images/generations"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.openai.com/v1/images/generations"))
                .andRespond(withSuccess("{\"data\":[{}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.invoke(route, "一只安静的猫", "1024x1024"))
                .isInstanceOf(ModelInvocationException.class);
        assertThatThrownBy(() -> client.invoke(route, "一只安静的猫", "1024x1024"))
                .isInstanceOf(ModelInvocationException.class);
        server.verify();
    }

    @Test
    void rejectsPlatformRoutesAndNonPositiveTimeouts() {
        assertThatThrownBy(() -> client.invoke(ModelRouteDecision.platform(),
                "一只安静的猫", "1024x1024")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OpenAiCompatibleImageClient.production(objectMapper,
                Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
