package com.li.lipicturecloud.infrastructure.airuntime;

import com.li.lipicturecloud.application.airuntime.ConnectivityResult;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

class OpenAiCompatibleConnectivityTesterTest {

    private static final URI ENDPOINT = URI.create("https://api.deepseek.com/v1");

    private MockRestServiceServer server;
    private OpenAiCompatibleConnectivityTester tester;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        tester = new OpenAiCompatibleConnectivityTester(builder.build());
    }

    @Test
    void reportsReachableOn2xxWithBearerCredential() {
        server.expect(requestTo("https://api.deepseek.com/v1/models"))
                .andExpect(header("Authorization", "Bearer sk-test-key"))
                .andRespond(withSuccess());

        ConnectivityResult result = tester.test(ENDPOINT, "sk-test-key", ModelProvider.DEEPSEEK);

        assertThat(result).isEqualTo(ConnectivityResult.success());
        server.verify();
    }

    @Test
    void normalizesTrailingSlashBeforeProbing() {
        server.expect(requestTo("https://api.deepseek.com/v1/models"))
                .andRespond(withSuccess());

        assertThat(tester.test(URI.create("https://api.deepseek.com/v1/"), "sk",
                ModelProvider.DEEPSEEK).reachable()).isTrue();
    }

    @Test
    void mapsUnauthorizedAndForbiddenToCredentialRejected() {
        server.expect(requestTo("https://api.deepseek.com/v1/models"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo("https://api.deepseek.com/v1/models"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(tester.test(ENDPOINT, "sk", ModelProvider.DEEPSEEK).safeErrorCode())
                .isEqualTo(ConnectivityResult.CREDENTIAL_REJECTED);
        assertThat(tester.test(ENDPOINT, "sk", ModelProvider.DEEPSEEK).safeErrorCode())
                .isEqualTo(ConnectivityResult.CREDENTIAL_REJECTED);
        server.verify();
    }

    @Test
    void mapsOtherClientAndServerFailuresToUpstreamError() {
        server.expect(requestTo("https://api.deepseek.com/v1/models"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.deepseek.com/v1/models"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("https://api.deepseek.com/v1/models"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(tester.test(ENDPOINT, "sk", ModelProvider.DEEPSEEK).safeErrorCode())
                .isEqualTo(ConnectivityResult.UPSTREAM_ERROR);
        assertThat(tester.test(ENDPOINT, "sk", ModelProvider.DEEPSEEK).safeErrorCode())
                .isEqualTo(ConnectivityResult.UPSTREAM_ERROR);
        assertThat(tester.test(ENDPOINT, "sk", ModelProvider.DEEPSEEK).safeErrorCode())
                .isEqualTo(ConnectivityResult.UPSTREAM_ERROR);
        server.verify();
    }

    @Test
    void mapsSocketTimeoutToTimeoutAndRefusalToUpstreamError() {
        server.expect(requestTo("https://api.deepseek.com/v1/models"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));
        server.expect(requestTo("https://api.deepseek.com/v1/models"))
                .andRespond(withException(new ConnectException("refused")));

        assertThat(tester.test(ENDPOINT, "sk", ModelProvider.DEEPSEEK).safeErrorCode())
                .isEqualTo(ConnectivityResult.UPSTREAM_TIMEOUT);
        assertThat(tester.test(ENDPOINT, "sk", ModelProvider.DEEPSEEK).safeErrorCode())
                .isEqualTo(ConnectivityResult.UPSTREAM_ERROR);
        server.verify();
    }

    @Test
    void productionFactoryRejectsNonPositiveTimeouts() {
        assertThatThrownBy(() -> OpenAiCompatibleConnectivityTester.production(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OpenAiCompatibleConnectivityTester.production(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
