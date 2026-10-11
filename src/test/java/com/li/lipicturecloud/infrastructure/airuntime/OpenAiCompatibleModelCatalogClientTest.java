package com.li.lipicturecloud.infrastructure.airuntime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.ConnectivityResult;
import com.li.lipicturecloud.application.airuntime.ModelCatalogClient;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleModelCatalogClientTest {

    private static final URI ENDPOINT = URI.create("https://api.deepseek.com/v1/");

    private MockRestServiceServer server;
    private OpenAiCompatibleModelCatalogClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiCompatibleModelCatalogClient(builder.build(), new ObjectMapper());
    }

    @Test
    void listsConversationalModelsSortedWithoutNonChatOrUnsafeIds() {
        server.expect(requestTo("https://api.deepseek.com/v1/models"))
                .andExpect(header("Authorization", "Bearer sk-key"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"deepseek-reasoner"},{"id":"deepseek-chat"},
                        {"id":"text-embedding-3-small"},{"id":"whisper-1"},{"id":"gpt-image-1"},
                        {"id":"bad id with spaces"},{"id":"deepseek-chat"},{"id":"deepseek-ai/DeepSeek-V3"}]}
                        """, MediaType.APPLICATION_JSON));

        ModelCatalogClient.Catalog catalog = client.listModels(ENDPOINT, "sk-key", ModelProvider.DEEPSEEK);

        assertThat(catalog.result().reachable()).isTrue();
        assertThat(catalog.listed()).isTrue();
        assertThat(catalog.modelCodes())
                .containsExactly("deepseek-ai/DeepSeek-V3", "deepseek-chat", "deepseek-reasoner");
        server.verify();
    }

    @Test
    void stripsGeminiModelsPrefix() {
        assertThat(client.parseModels("{\"data\":[{\"id\":\"models/gemini-2.5-flash\"}]}"))
                .containsExactly("gemini-2.5-flash");
    }

    @Test
    void anthropicCatalogAlsoSendsNativeHeaders() {
        server.expect(requestTo("https://api.anthropic.com/v1/models"))
                .andExpect(header("x-api-key", "sk-ant"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"claude-sonnet-5-5\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.listModels(URI.create("https://api.anthropic.com/v1"), "sk-ant",
                ModelProvider.ANTHROPIC).modelCodes()).containsExactly("claude-sonnet-5-5");
        server.verify();
    }

    @Test
    void missingCatalogEndpointIsUnlistedButRejectedKeyFails() {
        server.expect(requestTo("https://api.deepseek.com/v1/models")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.deepseek.com/v1/models")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo("https://api.deepseek.com/v1/models")).andRespond(withSuccess("not json", MediaType.TEXT_PLAIN));

        ModelCatalogClient.Catalog missing = client.listModels(ENDPOINT, "sk", ModelProvider.DEEPSEEK);
        assertThat(missing.result().reachable()).isTrue();
        assertThat(missing.listed()).isFalse();
        assertThat(client.listModels(ENDPOINT, "sk", ModelProvider.DEEPSEEK).result().safeErrorCode())
                .isEqualTo(ConnectivityResult.CREDENTIAL_REJECTED);
        assertThat(client.listModels(ENDPOINT, "sk", ModelProvider.DEEPSEEK).listed()).isFalse();
        server.verify();
    }

    @Test
    void probeSendsOneTokenChatRequest() {
        server.expect(requestTo("https://api.deepseek.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-key"))
                .andExpect(content().json("{\"model\":\"deepseek-chat\",\"max_tokens\":1,\"stream\":false}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.probeChat(ENDPOINT, "sk-key", ModelProvider.DEEPSEEK, "deepseek-chat"))
                .isEqualTo(ConnectivityResult.success());
        server.verify();
    }

    @Test
    void probeRetriesReasoningModelsWithCompletionTokenLimit() {
        server.expect(requestTo("https://api.deepseek.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        server.expect(requestTo("https://api.deepseek.com/v1/chat/completions"))
                .andExpect(content().json("{\"max_completion_tokens\":16}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.probeChat(ENDPOINT, "sk", ModelProvider.OPENAI, "o4-mini").reachable()).isTrue();
        server.verify();
    }

    @Test
    void probeMapsFailuresToSafeCodes() {
        server.expect(requestTo("https://api.deepseek.com/v1/chat/completions")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.deepseek.com/v1/chat/completions")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo("https://api.deepseek.com/v1/chat/completions")).andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED));
        server.expect(requestTo("https://api.deepseek.com/v1/chat/completions")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo("https://api.deepseek.com/v1/chat/completions"))
                .andRespond(withException(new SocketTimeoutException("slow")));

        assertThat(code("m")).isEqualTo(ConnectivityResult.MODEL_UNAVAILABLE);
        assertThat(code("m")).isEqualTo(ConnectivityResult.CREDENTIAL_REJECTED);
        assertThat(code("m")).isEqualTo(ConnectivityResult.QUOTA_EXHAUSTED);
        assertThat(code("m")).isEqualTo(ConnectivityResult.UPSTREAM_ERROR);
        assertThat(code("m")).isEqualTo(ConnectivityResult.UPSTREAM_TIMEOUT);
        server.verify();
    }

    private String code(String model) {
        return client.probeChat(ENDPOINT, "sk", ModelProvider.DEEPSEEK, model).safeErrorCode();
    }
}
