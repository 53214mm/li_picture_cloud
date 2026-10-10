package com.li.lipicturecloud.infrastructure.airuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.*;
import com.li.lipicturecloud.domain.airuntime.*;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class BoundedDeepSeekProbeClientTest {
    ModelRouteDecision route(String endpoint) {
        return ModelRouteDecision.byok(ModelConnection.restore(9L,7L,ModelProvider.DEEPSEEK,"Synthetic",
                URI.create(endpoint),"deepseek-flash",5L,true,1L),"synthetic-key");
    }
    BoundedDeepSeekProbeClient client() {
        HttpClient transport=mock(HttpClient.class);
        when(transport.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
        return new BoundedDeepSeekProbeClient(transport);
    }
    @Test void preflightDiagnosticsClassifyTypesWithoutReadingUnsafeMessages() {
        assertThat(BoundedDeepSeekProbeClient.preflightFailureCode(new RuntimeException("private", new java.net.UnknownHostException("private")))).isEqualTo("DNS_FAILURE");
        assertThat(BoundedDeepSeekProbeClient.preflightFailureCode(new java.net.http.HttpConnectTimeoutException("private"))).isEqualTo("CONNECT_TIMEOUT");
        assertThat(BoundedDeepSeekProbeClient.preflightFailureCode(new java.net.http.HttpTimeoutException("private"))).isEqualTo("NETWORK_IO_FAILURE");
        assertThat(BoundedDeepSeekProbeClient.preflightFailureCode(new javax.net.ssl.SSLHandshakeException("private"))).isEqualTo("TLS_FAILURE");
        assertThat(BoundedDeepSeekProbeClient.preflightFailureCode(new java.net.ConnectException("private"))).isEqualTo("CONNECT_FAILURE");
        assertThat(BoundedDeepSeekProbeClient.preflightFailureCode(new SecurityException("private"))).isEqualTo("PERMISSION_DENIED");
        assertThat(BoundedDeepSeekProbeClient.preflightFailureCode(new java.net.SocketException("Operation not permitted"))).isEqualTo("NETWORK_IO_FAILURE");
        assertThat(BoundedDeepSeekProbeClient.preflightFailureCode(new IllegalStateException("private"))).isEqualTo("UNKNOWN");
        var tls = new javax.net.ssl.SSLHandshakeException("private");
        tls.initCause(new java.io.IOException("private"));
        assertThat(BoundedDeepSeekProbeClient.preflightFailureCode(new java.net.ConnectException("private") {{ initCause(tls); }})).isEqualTo("TLS_FAILURE");
        Throwable trap = new java.net.UnknownHostException() {
            @Override public String getMessage() { throw new AssertionError("MESSAGE_READ"); }
            @Override public String toString() { throw new AssertionError("TOSTRING_READ"); }
        };
        assertThat(BoundedDeepSeekProbeClient.preflightFailureCode(trap)).isEqualTo("DNS_FAILURE");
    }
    @Test void preflightSuccessAndFailureRecordsUseConsumerAndNoAuthorization() throws Exception {
        HttpClient transport=mock(HttpClient.class);
        when(transport.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
        java.net.http.HttpResponse<?> response=mock(java.net.http.HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        doReturn(response).when(transport).send(any(),any());
        java.util.List<String> records=new java.util.ArrayList<>();
        BoundedDeepSeekProbeClient.preflight(transport,records::add);
        assertThat(records).singleElement().asString().contains("\"httpStatus\":401","\"allowedToContinue\":true","\"modelAuthVerified\":false");
        var request=org.mockito.ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
        verify(transport).send(request.capture(),any());
        assertThat(request.getValue().uri()).isEqualTo(URI.create("https://api.deepseek.com/models"));
        assertThat(request.getValue().method()).isEqualTo("GET");
        assertThat(request.getValue().headers().firstValue("Authorization")).isEmpty();
        records.clear();
        doThrow(new java.net.SocketException("private input Operation not permitted")).when(transport).send(any(),any());
        assertThatThrownBy(() -> BoundedDeepSeekProbeClient.preflight(transport,records::add)).isInstanceOf(RuntimeException.class);
        assertThat(records).singleElement().asString().contains("NETWORK_IO_FAILURE").doesNotContain("private", "Operation", "PERMISSION_DENIED");
    }
    @Test void preflightAllowsOnlyPublicOrExpectedUnauthenticatedResponse() {
        assertThat(BoundedDeepSeekProbeClient.preflightStatusAllowed(200)).isTrue();
        assertThat(BoundedDeepSeekProbeClient.preflightStatusAllowed(401)).isTrue();
        for(int status : List.of(301, 302, 403, 404, 429, 500, 503))
            assertThat(BoundedDeepSeekProbeClient.preflightStatusAllowed(status)).isFalse();
    }
    @Test void exactBodyPinsTokensModelThinkingAndUsage() throws Exception {
        var body=new ObjectMapper().readTree(client().encodeBody(route("https://api.deepseek.com"),List.of(new ChatTurn("user","合成测试")),256));
        assertThat(body.path("max_tokens").intValue()).isEqualTo(256);
        assertThat(body.path("thinking").path("type").textValue()).isEqualTo("disabled");
        assertThat(body.path("model").textValue()).isEqualTo("deepseek-flash");
        assertThat(body.path("stream_options").path("include_usage").booleanValue()).isTrue();
        assertThatThrownBy(()->client().encodeBody(route("https://api.deepseek.com"),List.of(),null)).isInstanceOf(IllegalArgumentException.class);
        for(String endpoint:List.of("https://evil.example","https://api.deepseek.com/v1","https://api.deepseek.com:443"))
            assertThatThrownBy(()->BoundedDeepSeekProbeClient.validate(route(endpoint))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->BoundedDeepSeekProbeClient.validate(ModelRouteDecision.platform())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void observationsOnlyIncludeSafeNumericUsageAndAllowlistedFinishReason() {
        var client=client();
        assertThat(client.extractDelta("data: {\"choices\":[{\"delta\":{\"content\":\"合成\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":3,\"total_tokens\":15,\"secret\":\"bad\"}}")).isEqualTo("合成");
        assertThat(client.takeObservations()).containsEntry("prompt_tokens",12L).containsEntry("finish_reason","stop").doesNotContainKey("secret");
        client.extractDelta("data: {\"choices\":[{\"finish_reason\":\"arbitrary provider text\"}],\"usage\":{\"prompt_tokens\":\"secret\",\"total_tokens\":-1}}");
        assertThat(client.takeObservations()).isEmpty();
        client.extractDelta("data: [DONE]");
    }
}
