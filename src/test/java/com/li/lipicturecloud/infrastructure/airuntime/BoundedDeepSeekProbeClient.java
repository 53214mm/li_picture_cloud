package com.li.lipicturecloud.infrastructure.airuntime;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.li.lipicturecloud.application.airuntime.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

/** Test-only adapter. Never instantiated by the default offline probe. */
public final class BoundedDeepSeekProbeClient extends OpenAiCompatibleLanguageClient {
    public static final URI ENDPOINT = URI.create("https://api.deepseek.com");
    public static final String MODEL = "deepseek-flash";
    public static final int TOKENS = 256;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Object> observations = new LinkedHashMap<>();

    public BoundedDeepSeekProbeClient() {
        this(transport());
    }
    public static HttpClient transport() {
        System.setProperty("jdk.httpclient.disableRetryConnect", "true");
        System.setProperty("jdk.httpclient.enableAllMethodRetry", "false");
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(15)).build();
    }
    public static void preflight(java.util.function.Consumer<String> output) throws Exception {
        try { preflight(transport(), output); }
        catch (PreflightFailure safe) { throw safe; }
        catch (Throwable failure) { reportPreflightFailure(failure, output); }
    }
    static void preflight(HttpClient transport, java.util.function.Consumer<String> output) throws Exception {
        try {
            if (transport.followRedirects() != HttpClient.Redirect.NEVER) throw new SecurityException();
            var request = java.net.http.HttpRequest.newBuilder(ENDPOINT.resolve("/models"))
                    .timeout(Duration.ofSeconds(20)).GET().build();
            var response = transport.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            boolean allowed = preflightStatusAllowed(response.statusCode());
            output.accept("{\"preflight\":\"tls_reachable_only\",\"modelAuthVerified\":false,\"httpStatus\":"
                    + response.statusCode() + ",\"allowedToContinue\":" + allowed + "}");
            if (!allowed) throw new PreflightFailure();
        } catch (PreflightFailure safe) { throw safe; }
        catch (Throwable failure) { reportPreflightFailure(failure, output); }
    }
    private static void reportPreflightFailure(Throwable failure, java.util.function.Consumer<String> output) {
        output.accept("{\"preflight\":\"failed\",\"modelAuthVerified\":false,\"allowedToContinue\":false,\"code\":\""
                + preflightFailureCode(failure) + "\"}");
        throw new PreflightFailure(); // Never retain the unsafe cause.
    }
    private static final class PreflightFailure extends RuntimeException { }
    /** Types only: generic SocketException is never interpreted as proof of EPERM. */
    static String preflightFailureCode(Throwable failure) {
        String result = "UNKNOWN";
        int best = 0;
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8 && seen.add(current); depth++) {
            String code = "UNKNOWN";
            int priority = 0;
            if (current instanceof SecurityException) { code = "PERMISSION_DENIED"; priority = 3; }
            else if (current instanceof javax.net.ssl.SSLException) { code = "TLS_FAILURE"; priority = 3; }
            else if (current instanceof java.net.UnknownHostException || current instanceof java.nio.channels.UnresolvedAddressException) {
                code = "DNS_FAILURE"; priority = 3;
            } else if (current instanceof java.net.http.HttpConnectTimeoutException) {
                code = "CONNECT_TIMEOUT"; priority = 3;
            } else if (current instanceof java.net.ConnectException) { code = "CONNECT_FAILURE"; priority = 2; }
            else if (current instanceof IOException) { code = "NETWORK_IO_FAILURE"; priority = 1; }
            if (priority > 0 && priority >= best) { result = code; best = priority; }
            current = current.getCause();
        }
        return result;
    }
    static boolean preflightStatusAllowed(int status) { return status == 200 || status == 401; }
    BoundedDeepSeekProbeClient(HttpClient client) {
        super(new ObjectMapper(), client, Duration.ofSeconds(40));
        if (client.followRedirects() != HttpClient.Redirect.NEVER) throw new IllegalArgumentException("REDIRECTS_FORBIDDEN");
    }
    public static void validate(ModelRouteDecision route) {
        if (!route.isByok() || !ENDPOINT.equals(route.connection().endpointUri())
                || !MODEL.equals(route.connection().modelCode())) throw new IllegalArgumentException("ROUTE_FORBIDDEN");
    }
    @Override String encodeBody(ModelRouteDecision route, List<ChatTurn> turns, Integer maxTokens) throws IOException {
        validate(route);
        if (!Integer.valueOf(TOKENS).equals(maxTokens)) throw new IllegalArgumentException("TOKEN_BOUND_REQUIRED");
        ObjectNode body = (ObjectNode) mapper.readTree(super.encodeBody(route, turns, maxTokens));
        body.putObject("thinking").put("type", "disabled");
        body.putObject("stream_options").put("include_usage", true);
        return mapper.writeValueAsString(body);
    }
    @Override String extractDelta(String line) {
        // The production parser owns errors and text. Never log frames, routes or exception messages.
        String delta = super.extractDelta(line);
        String data = line.strip();
        if (data.startsWith("data:")) {
            try {
                JsonNode n = mapper.readTree(data.substring(5).strip());
                if (n == null) return delta;
                for (String field : List.of("prompt_tokens", "completion_tokens", "total_tokens")) {
                    JsonNode v = n.path("usage").path(field);
                    if (v.isIntegralNumber() && v.canConvertToLong() && v.longValue() >= 0)
                        observations.put(field, v.longValue());
                }
                String finish = n.path("choices").path(0).path("finish_reason").asText("");
                if (Set.of("stop", "length", "content_filter", "tool_calls", "insufficient_system_resource").contains(finish))
                    observations.put("finish_reason", finish);
            } catch (IOException ignored) { /* DONE/non-JSON frames already handled upstream */ }
        }
        return delta;
    }
    public Map<String, Object> takeObservations() {
        Map<String, Object> result = Map.copyOf(observations);
        observations.clear();
        return result;
    }
}
