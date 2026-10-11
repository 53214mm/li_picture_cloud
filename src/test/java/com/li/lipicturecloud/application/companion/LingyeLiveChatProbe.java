package com.li.lipicturecloud.application.companion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.*;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.airuntime.*;
import com.li.lipicturecloud.domain.companion.*;
import com.li.lipicturecloud.infrastructure.airuntime.BoundedDeepSeekProbeClient;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Service-path probe, NOT HTTP/Spring transaction/browser/vault end-to-end acceptance.
 * Fixtures are synthetic and in memory. Default never reads credentials or constructs an HTTP client.
 */
public final class LingyeLiveChatProbe {
    static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
    static final String MEMORY = "合成图片里，粉色双马尾的机械女仆站在蓝色窗边。";
    static final String UNRELATED_MEMORY = "合成图片里，一只橘猫坐在绿色椅子上。";
    static final List<String> QUERIES = List.of("讲个笑话", "刚才那个有点AI味，换个自然一点的说法", "你好呀", "还记得图片 101 里的机械女仆吗？");
    static final String OFFLINE = "[OFFLINE FIXTURE: no model called] 绫页的离线测试回复";
    record Options(boolean live, int scenarios) { }
    static Options parse(String[] args) {
        boolean live = false, modeSeen = false;
        int count = 4;
        boolean countSeen = false;
        for (String arg : args) {
            if (arg.equals("--live") || arg.equals("--dry-run")) {
                if (modeSeen) throw new IllegalArgumentException("INVALID_ARGUMENTS");
                modeSeen = true; live = arg.equals("--live");
            } else if (arg.startsWith("--scenarios=") && !countSeen) {
                countSeen = true;
                try { count = Integer.parseInt(arg.substring(12)); }
                catch (RuntimeException e) { throw new IllegalArgumentException("INVALID_ARGUMENTS"); }
            } else throw new IllegalArgumentException("INVALID_ARGUMENTS");
        }
        if (count < 1 || count > 4) throw new IllegalArgumentException("INVALID_ARGUMENTS");
        return new Options(live, count);
    }
    public static void main(String[] args) {
        // Standalone process only: cancellation races must never dump raw provider causes.
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                System.err.println("{\"status\":\"failed\",\"code\":\"UNCAUGHT_PROBE_ERROR\"}"));
        reactor.core.publisher.Hooks.onErrorDropped(ignored -> { });
        List<String> safeRecords = new ArrayList<>();
        Consumer<String> output = json -> { safeRecords.add(json); System.out.println(json); };
        int exitCode = 0;
        try {
            if (args.length == 1 && args[0].equals("--preflight-network")) {
                BoundedDeepSeekProbeClient.preflight(output);
            } else {
                Options options = parse(args);
                execute(options, () -> readKey(System.in), BoundedDeepSeekProbeClient::new, output);
            }
        } catch (Throwable failure) {
            // Deliberately no exception message/stacktrace, since these may contain provider inputs.
            output.accept("{\"status\":\"failed\",\"code\":\"PROBE_FAILED\"}");
            exitCode = 1;
        }
        try {
            java.nio.file.Path directory = java.nio.file.Path.of("target");
            java.nio.file.Files.createDirectories(directory);
            java.nio.file.Path destination = directory.resolve("lingye-live-report-" + UUID.randomUUID() + ".json");
            java.nio.file.Path temporary = java.nio.file.Files.createTempFile(directory, ".lingye-report-", ".tmp");
            try {
                java.nio.file.Files.writeString(temporary, "[" + String.join(",", safeRecords) + "]\n", java.nio.charset.StandardCharsets.UTF_8);
                java.nio.file.Files.move(temporary, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } finally { java.nio.file.Files.deleteIfExists(temporary); }
            System.out.println(new ObjectMapper().writeValueAsString(Map.of("report", destination.toString())));
        } catch (Throwable failure) {
            System.out.println("{\"status\":\"failed\",\"code\":\"REPORT_WRITE_FAILED\"}");
            exitCode = 1;
        }
        if (exitCode != 0) System.exit(exitCode);
    }
    /** Anonymous pipe only, bounded ASCII credential line; never reads environment or files. */
    static String readKey(java.io.InputStream input) {
        StringBuilder key = new StringBuilder();
        try {
            for (;;) {
                int value = input.read();
                if (value == -1 || value == '\n') break;
                if (value < 33 || value > 126 || key.length() >= 512) throw new IllegalStateException("KEY_INVALID");
                key.append((char) value);
            }
        } catch (java.io.IOException ignored) { throw new IllegalStateException("KEY_INPUT_FAILED"); }
        if (key.isEmpty()) throw new IllegalStateException("KEY_REQUIRED");
        return key.toString();
    }
    static List<Result> execute(Options options, Supplier<String> credentialInput,
                                Supplier<BoundedDeepSeekProbeClient> factory, Consumer<String> out) throws Exception {
        if (options.scenarios() < 1 || options.scenarios() > 4) throw new IllegalArgumentException("INVALID_ARGUMENTS");
        String key = "offline-synthetic-key";
        BoundedDeepSeekProbeClient client = null;
        if (options.live()) {
            key = credentialInput.get();
            if (key == null || key.isBlank() || key.length() > 512) throw new IllegalStateException("KEY_REQUIRED");
            client = factory.get();
        }
        LanguageModelInvoker delegate = client == null ? new LanguageModelInvoker() {
            public Flux<String> stream(ModelRouteDecision r, List<ChatTurn> t) { throw new AssertionError("UNBOUNDED_FORBIDDEN"); }
            public Flux<String> streamBounded(ModelRouteDecision r, List<ChatTurn> t, int max) { return Flux.just(OFFLINE); }
        } : client;
        BoundedInvoker bounded = new BoundedInvoker(delegate);
        List<Result> results = new ArrayList<>();
        Fixture fixture = null;
        out.accept(new ObjectMapper().writeValueAsString(Map.of(
                "scope", "synthetic-service-path", "untested", "HTTP/Spring transaction/browser/vault end-to-end",
                "live", options.live(), "provider", "DEEPSEEK", "model", BoundedDeepSeekProbeClient.MODEL,
                "personaVersion", LingyePersona.VERSION, "requestCap", 4, "maxTokens", 256)));
        try {
        for (int i = 0; i < options.scenarios(); i++) {
            // Only the intentional joke follow-up shares history. Greeting and recall are isolated.
            if (i != 1) fixture = new Fixture(key, bounded);
            long start = System.nanoTime();
            java.util.concurrent.atomic.AtomicLong first = new java.util.concurrent.atomic.AtomicLong(-1);
            String reply = String.join("", fixture.service.chat(AuthorizationSubject.user(7L), QUERIES.get(i))
                    .doOnNext(delta -> { if (!delta.isEmpty()) first.compareAndSet(-1, (System.nanoTime() - start) / 1_000_000); })
                    .collectList().block(Duration.ofSeconds(70)));
            if (options.live()) reply = reply.replace(key, "[REDACTED]");
            Map<String, Object> usage = client == null ? Map.of() : client.takeObservations();
            boolean incomplete = incomplete(options.live(), reply, usage);
            Result result = new Result(i + 1, options.live(), QUERIES.get(i), reply, fixture.stored.size(), usage,
                    first.get(), (System.nanoTime() - start) / 1_000_000,
                    incomplete ? "incomplete" : options.live() ? "completed" : "offline_fixture");
            results.add(result);
            out.accept(new ObjectMapper().writeValueAsString(result));
        }
        } catch (Throwable failure) {
            out.accept("{\"status\":\"partial_failure\",\"code\":\"" + safeFailureCode(failure) + "\",\"completedScenarios\":"
                    + results.size() + ",\"invocationAttempts\":" + bounded.requests.get()
                    + ",\"providerRequests\":" + (options.live() ? bounded.requests.get() : 0) + "}");
            throw new IllegalStateException("PROBE_FAILED");
        }
        long incompleteCount = results.stream().filter(r -> r.status().equals("incomplete")).count();
        out.accept("{\"status\":\"" + (incompleteCount > 0 ? "incomplete" : "finished")
                + "\",\"completedScenarios\":" + results.size() + ",\"incompleteScenarios\":" + incompleteCount
                + ",\"invocationAttempts\":" + bounded.requests.get()
                + ",\"providerRequests\":" + (options.live() ? bounded.requests.get() : 0) + "}");
        return results;
    }
    static String safeFailureCode(Throwable failure) {
        Throwable current = failure;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int depth = 0; current != null && depth < 8 && visited.add(current); depth++) {
            if (current instanceof ModelInvocationException invocation) {
                String code = invocation.safeErrorCode();
                if (code != null && Set.of("CREDENTIAL_REJECTED", "UPSTREAM_TIMEOUT", "UPSTREAM_ERROR").contains(code)) return code;
            }
            current = current.getCause();
        }
        return "PROBE_FAILED";
    }
    static boolean incomplete(boolean live, String reply, Map<String, Object> usage) {
        return reply.isBlank() || live && !"stop".equals(usage.get("finish_reason"));
    }
    record Result(int scenario, boolean live, String syntheticInput, String syntheticOutput, int storedMessages, Map<String, Object> usage,
                  long firstTokenMillis, long completedMillis, String status) { }
    static final class BoundedInvoker implements LanguageModelInvoker {
        final LanguageModelInvoker delegate;
        final AtomicInteger requests = new AtomicInteger();
        final List<List<ChatTurn>> captured = new ArrayList<>();
        BoundedInvoker(LanguageModelInvoker delegate) { this.delegate = delegate; }
        public Flux<String> stream(ModelRouteDecision route, List<ChatTurn> turns) {
            BoundedDeepSeekProbeClient.validate(route);
            captured.add(List.copyOf(turns));
            return Flux.defer(() -> {
                if (requests.incrementAndGet() > 4) return Flux.error(new IllegalStateException("REQUEST_CAP"));
                return delegate.streamBounded(route, turns, 256);
            });
        }
    }
    static ModelConnection connection() {
        return ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK, "Synthetic probe",
                BoundedDeepSeekProbeClient.ENDPOINT, BoundedDeepSeekProbeClient.MODEL, 5L, true, 1L);
    }
    static final class Fixture {
        final List<CompanionChatMessage> stored = new ArrayList<>();
        final CompanionChatService service;
        final SpaceAuthorizationAccessService authorization = mock(SpaceAuthorizationAccessService.class);
        @SuppressWarnings("unchecked") Fixture(String key, LanguageModelInvoker invoker) {
            Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
            CompanionRepository companions = mock(CompanionRepository.class);
            when(companions.findByOwnerId(7L)).thenReturn(Optional.of(Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L)));
            CompanionChatMessageRepository messages = mock(CompanionChatMessageRepository.class);
            when(messages.append(any())).thenAnswer(call -> {
                CompanionChatMessage saved = call.<CompanionChatMessage>getArgument(0).withId(stored.size() + 1L);
                stored.add(saved); return saved;
            });
            when(messages.findRecent(eq(11L), anyInt())).thenAnswer(call -> {
                List<CompanionChatMessage> recent = new ArrayList<>(stored); Collections.reverse(recent);
                return recent.stream().limit(call.<Integer>getArgument(1)).toList();
            });
            when(messages.findById(anyLong())).thenAnswer(call -> stored.stream()
                    .filter(message -> message.id().equals(call.<Long>getArgument(0))).findFirst());
            when(companions.findByOwnerIdForUpdate(7L)).thenAnswer(call -> companions.findByOwnerId(7L));
            org.springframework.transaction.PlatformTransactionManager manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
            when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
            CompanionRelationshipRepository relationships = mock(CompanionRelationshipRepository.class);
            CompanionMemoryRepository memories = mock(CompanionMemoryRepository.class);
            CompanionMemory memory = CompanionMemory.candidate(11L, 7L, 101L, 21L, MemorySourceType.VISUAL,
                    MEMORY, new BigDecimal("0.8"), NOW).withId(31L).confirm(NOW);
            CompanionMemory unrelated = CompanionMemory.candidate(11L, 7L, 102L, 22L, MemorySourceType.VISUAL,
                    UNRELATED_MEMORY, new BigDecimal("0.8"), NOW).withId(32L).confirm(NOW);
            when(memories.findConfirmed(eq(11L), eq(7L), anyInt())).thenReturn(List.of(unrelated, memory));
            CompanionMemoryService memoryService = new CompanionMemoryService(companions, memories,
                    authorization, mock(CompanionViewAssembler.class), clock);
            CompanionFeatureProperties properties = new CompanionFeatureProperties();
            TaskRoutingRuleRepository rules = mock(TaskRoutingRuleRepository.class);
            when(rules.findBySubjectAndTask(7L, ModelTask.LANGUAGE_AGENT)).thenReturn(Optional.of(TaskRoutingRule.create(7L, ModelTask.LANGUAGE_AGENT, 9L)));
            // Credential boundary injected only in memory; no real vault/cipher/database is constructed.
            ByokConnectionResolver resolver = new ByokConnectionResolver(null, null, null, null) {
                @Override public ModelRouteDecision resolveByok(long subject, long id, String label) {
                    if (subject != 7L || id != 9L) throw new IllegalStateException("SYNTHETIC_ROUTE_ONLY");
                    return ModelRouteDecision.byok(connection(), key);
                }
            };
            ModelCapabilityProfileRepository profiles = mock(ModelCapabilityProfileRepository.class);
            when(profiles.findLatestByConnectionId(9L)).thenReturn(Optional.of(ModelCapabilityProfile.snapshot(9L, 7L,
                    ModelProvider.DEEPSEEK, BoundedDeepSeekProbeClient.MODEL, ModelCapabilities.unknown().withVerifiedText(), NOW)));
            LanguageRouter router = new LanguageRouter(rules, resolver, new ModelCapabilityProfileService(profiles, null, clock));
            CompanionChatPreferenceRepository preferences = mock(CompanionChatPreferenceRepository.class);
            ModelQuickSetupService quick = mock(ModelQuickSetupService.class);
            when(quick.current(7L)).thenReturn(new com.li.lipicturecloud.application.airuntime.view.QuickConnectionView(
                    com.li.lipicturecloud.application.airuntime.view.QuickConnectionView.Status.CONNECTED,
                    ModelProvider.DEEPSEEK, "DeepSeek", BoundedDeepSeekProbeClient.MODEL, 9L));
            ObjectProvider<ChatModel> platform = mock(ObjectProvider.class);
            when(platform.getIfAvailable()).thenThrow(new AssertionError("PLATFORM_FORBIDDEN"));
            service = new CompanionChatService(companions, messages, mock(CompanionMoodRepository.class), CompanionMoodRules.v1(),
                    relationships, memoryService, new CompanionChatContextAssembler(companions, relationships),
                    mock(ChatQuotaGuard.class), properties, platform, router, invoker, mock(ModelUsageService.class),
                    mock(PlatformTrialLedgerService.class), new CompanionChatModeService(preferences, quick, properties, mock(org.springframework.beans.factory.ObjectProvider.class)),
                    // 探针不连向量库；聊天经历整理会额外调模型，这里用空实现，不占 4 次请求上限。
                    new CompanionMemoryRecall(new com.li.lipicturecloud.infrastructure.companion.UnavailableCompanionMemoryIndex(),
                            ids -> Map.of(), 0.45),
                    new CompanionChatSessions(mock(com.li.lipicturecloud.domain.companion.ChatSessionBoundaryRepository.class),
                            properties),
                    mock(CompanionEpisodeService.class), new CompanionChatWrites(companions, messages,
                            mock(com.li.lipicturecloud.domain.companion.CompanionChatEpisodeRepository.class), manager), clock);
        }
    }
}
