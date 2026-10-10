package com.li.lipicturecloud.application.companion;
import com.li.lipicturecloud.application.airuntime.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class LingyeLiveChatProbeTest {
    @Test void defaultAndDryRunNeverReadCredentialPipeOrConstructNetworkClient() throws Exception {
        for (String[] args : List.of(new String[0], new String[]{"--dry-run"})) {
            var results = LingyeLiveChatProbe.execute(LingyeLiveChatProbe.parse(args),
                    () -> { throw new AssertionError("CREDENTIAL_READ"); },
                    () -> { throw new AssertionError("NETWORK_CONSTRUCTION"); }, ignored -> {});
            assertThat(results).hasSize(4);
            assertThat(results).allSatisfy(r -> { assertThat(r.live()).isFalse(); assertThat(r.syntheticOutput()).contains("OFFLINE FIXTURE"); });
            assertThat(results.stream().map(LingyeLiveChatProbe.Result::storedMessages).toList()).containsExactly(2,4,2,2);
        }
    }
    @Test void anonymousPipeCredentialReadIsBounded() {
        assertThat(LingyeLiveChatProbe.readKey(new java.io.ByteArrayInputStream("synthetic-key\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))).isEqualTo("synthetic-key");
        for (String key : List.of("", "a".repeat(513), "invalid key\n"))
            assertThatThrownBy(() -> LingyeLiveChatProbe.readKey(new java.io.ByteArrayInputStream(key.getBytes(java.nio.charset.StandardCharsets.US_ASCII))))
                    .isInstanceOf(IllegalStateException.class);
    }
    @Test void onlyExplicitStopWithTextCountsAsCompleteLiveResult() {
        assertThat(LingyeLiveChatProbe.incomplete(true, "synthetic", Map.of("finish_reason", "stop"))).isFalse();
        assertThat(LingyeLiveChatProbe.incomplete(true, "", Map.of("finish_reason", "stop"))).isTrue();
        assertThat(LingyeLiveChatProbe.incomplete(true, "partial synthetic", Map.of())).isTrue();
        for(String reason : List.of("length", "content_filter", "tool_calls", "insufficient_system_resource"))
            assertThat(LingyeLiveChatProbe.incomplete(true, "partial synthetic", Map.of("finish_reason", reason))).isTrue();
    }
    @Test void safeFailureMappingNeverUsesMessagesOrArbitraryCodes() {
        for(String code : List.of("CREDENTIAL_REJECTED", "UPSTREAM_TIMEOUT", "UPSTREAM_ERROR"))
            assertThat(LingyeLiveChatProbe.safeFailureCode(new RuntimeException("unsafe text", new ModelInvocationException(code,"unsafe body")))).isEqualTo(code);
        assertThat(LingyeLiveChatProbe.safeFailureCode(new ModelInvocationException("arbitrary provider text", "unsafe body"))).isEqualTo("PROBE_FAILED");
        assertThat(LingyeLiveChatProbe.safeFailureCode(new RuntimeException("unsafe text"))).isEqualTo("PROBE_FAILED");
    }
    @Test void argumentGateRejectsAmbiguityOverridesAndExcessRequests() {
        assertThat(LingyeLiveChatProbe.parse(new String[0]).live()).isFalse();
        assertThat(LingyeLiveChatProbe.parse(new String[]{"--live"}).live()).isTrue();
        for (String[] args : List.of(new String[]{"--live", "--dry-run"}, new String[]{"--scenarios=5"},
                new String[]{"--scenarios=0"}, new String[]{"--scenarios=bad"}, new String[]{"--endpoint=evil"}, new String[]{"--key=secret"}))
            assertThatThrownBy(() -> LingyeLiveChatProbe.parse(args)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingKeyFailsBeforeConstructingNetworkClient() {
        assertThatThrownBy(() -> LingyeLiveChatProbe.execute(new LingyeLiveChatProbe.Options(true,4), () -> null,
                () -> { throw new AssertionError("NETWORK_CONSTRUCTION"); }, ignored -> {})).isInstanceOf(IllegalStateException.class);
    }
    @Test void realMemoryServiceFailsClosedBeforeInvocationWhenAuthorizationIsUnavailable() {
        var invoker = new LingyeLiveChatProbe.BoundedInvoker((r,t) -> { throw new AssertionError("MODEL_MUST_NOT_RUN"); });
        var fixture = new LingyeLiveChatProbe.Fixture("synthetic-key", invoker);
        org.mockito.Mockito.doThrow(new IllegalStateException("synthetic authorization outage"))
                .when(fixture.authorization).checkForUser(
                        com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW, 101L, 7L);
        assertThatThrownBy(() -> fixture.service.chat(
                com.li.lipicturecloud.manager.auth.model.AuthorizationSubject.user(7L), LingyeLiveChatProbe.QUERIES.get(3)).blockLast())
                .isInstanceOf(com.li.lipicturecloud.exception.BusinessException.class);
        assertThat(invoker.requests.get()).isZero();
    }
    @Test void realServiceUsesPersonaRelatedMemoryAndIsolatedStoredHistory() {
        var invoker = new LingyeLiveChatProbe.BoundedInvoker(new LanguageModelInvoker() {
            public Flux<String> stream(ModelRouteDecision r,List<ChatTurn> t) { throw new AssertionError("UNBOUNDED"); }
            public Flux<String> streamBounded(ModelRouteDecision r,List<ChatTurn> t,int cap) {
                assertThat(cap).isEqualTo(256); return Flux.just(LingyeLiveChatProbe.OFFLINE);
            }
        });
        var subject = com.li.lipicturecloud.manager.auth.model.AuthorizationSubject.user(7L);
        var fixture = new LingyeLiveChatProbe.Fixture("synthetic-key",invoker);
        fixture.service.chat(subject,LingyeLiveChatProbe.QUERIES.get(0)).blockLast();
        fixture.service.chat(subject,LingyeLiveChatProbe.QUERIES.get(1)).blockLast();
        var followup = invoker.captured.get(1);
        assertThat(followup).hasSize(4);
        assertThat(followup.get(1).content()).isEqualTo(LingyeLiveChatProbe.QUERIES.get(0));
        assertThat(followup.get(2).content()).isEqualTo(LingyeLiveChatProbe.OFFLINE);
        for(int i=2;i<4;i++) {
            fixture = new LingyeLiveChatProbe.Fixture("synthetic-key",invoker);
            fixture.service.chat(subject,LingyeLiveChatProbe.QUERIES.get(i)).blockLast();
        }
        assertThat(invoker.captured.get(2)).hasSize(2);
        assertThat(invoker.captured.get(2).get(0).content()).startsWith(LingyePersona.systemPrompt()).doesNotContain(LingyeLiveChatProbe.MEMORY);
        assertThat(invoker.captured.get(3).get(0).content()).contains(LingyeLiveChatProbe.MEMORY,"图片=101","已确认").doesNotContain(LingyeLiveChatProbe.UNRELATED_MEMORY);
        assertThatThrownBy(() -> invoker.stream(ModelRouteDecision.byok(LingyeLiveChatProbe.connection(),"synthetic-key"),
                List.of(new ChatTurn("user","hello"))).blockLast()).isInstanceOf(IllegalStateException.class);
        assertThat(invoker.requests.get()).isEqualTo(5); // fifth attempted subscription is denied before transport
    }
}
