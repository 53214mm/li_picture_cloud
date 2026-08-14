package com.li.lipicturecloud.infrastructure.airuntime;

import com.li.lipicturecloud.application.airuntime.ConnectivityResult;
import com.li.lipicturecloud.application.airuntime.ModelConnectivityTester;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;

/**
 * OpenAI 兼容端点的连通性探测：对 {@code {endpoint}/models} 发起带 Bearer 凭据的 GET，
 * 只看状态码不看正文；401/403 映射为凭据被拒，传输超时映射为超时，其余失败映射为上游错误。
 */
public class OpenAiCompatibleConnectivityTester implements ModelConnectivityTester {

    private final RestClient restClient;

    public OpenAiCompatibleConnectivityTester(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
    }

    /** 生产实例：JDK HttpClient，连接与请求共享同一超时预算。测试直接注入带本地 stub 的 RestClient。 */
    public static OpenAiCompatibleConnectivityTester production(Duration timeout) {
        Duration bounded = Objects.requireNonNull(timeout, "timeout");
        if (bounded.isZero() || bounded.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(bounded).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(bounded);
        return new OpenAiCompatibleConnectivityTester(RestClient.builder()
                .requestFactory(requestFactory).build());
    }

    @Override
    public ConnectivityResult test(URI endpointUri, String apiKey, ModelProvider provider) {
        Objects.requireNonNull(endpointUri, "endpointUri");
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(provider, "provider");
        URI probe = URI.create(endpointUri.toString().replaceFirst("/+$", "") + "/models");
        try {
            ResponseEntity<Void> response = restClient.get()
                    .uri(probe)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful()
                    ? ConnectivityResult.success()
                    : ConnectivityResult.failed(ConnectivityResult.UPSTREAM_ERROR);
        } catch (HttpClientErrorException rejected) {
            return ConnectivityResult.failed(rejected.getStatusCode().is4xxClientError()
                    && (rejected.getStatusCode().value() == 401
                    || rejected.getStatusCode().value() == 403)
                    ? ConnectivityResult.CREDENTIAL_REJECTED
                    : ConnectivityResult.UPSTREAM_ERROR);
        } catch (HttpServerErrorException serverError) {
            return ConnectivityResult.failed(ConnectivityResult.UPSTREAM_ERROR);
        } catch (ResourceAccessException transport) {
            return ConnectivityResult.failed(isTimeout(transport)
                    ? ConnectivityResult.UPSTREAM_TIMEOUT
                    : ConnectivityResult.UPSTREAM_ERROR);
        }
    }

    private static boolean isTimeout(ResourceAccessException transport) {
        Throwable cause = transport;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException
                    || cause instanceof InterruptedIOException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
