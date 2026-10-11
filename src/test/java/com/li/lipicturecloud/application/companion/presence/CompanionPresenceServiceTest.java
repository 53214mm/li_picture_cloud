package com.li.lipicturecloud.application.companion.presence;

import com.li.lipicturecloud.application.airuntime.*;
import com.li.lipicturecloud.domain.airuntime.*;
import com.li.lipicturecloud.domain.companion.*;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.model.dto.companion.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CompanionPresenceServiceTest {
    MutableClock clock = new MutableClock();
    LanguageRouter router = mock(LanguageRouter.class);
    LanguageModelInvoker invoker = mock(LanguageModelInvoker.class);
    PresenceQuotaGuard quota = mock(PresenceQuotaGuard.class);
    ModelUsageService usage = mock(ModelUsageService.class);
    CompanionRepository companions = mock(CompanionRepository.class);
    CompanionChatMessageRepository messages = mock(CompanionChatMessageRepository.class);
    CompanionPresenceService service;
    ModelRouteDecision route;
    @BeforeEach void setup() {
        service = service(true, true);
        route = ModelRouteDecision.byok(ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "test", URI.create("https://api.deepseek.com/v1"), "deepseek-chat", 5L, true, 0L), "fixture-only");
        when(router.decide(7)).thenReturn(route);
        when(companions.findByOwnerId(anyLong())).thenReturn(Optional.empty());
        when(invoker.streamBounded(any(), anyList(), eq(160))).thenReturn(Flux.just(
                "{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"我在呢！\"}"));
    }
    CompanionPresenceService service(boolean enabled, boolean real) {
        return new CompanionPresenceService(clock, router, invoker, quota, usage, companions, messages, enabled, real);
    }
    PresenceSessionView open(String mode, int budget, boolean muted) {
        return service.open(7, new PresenceSessionRequest(true, mode, budget, muted, "NORMAL"));
    }
    PresenceEventRequest event(PresenceSessionView session, long seq, String type) {
        String page = type.equals("PICTURE_OPENED") ? "PICTURE_DETAIL" : type.equals("UPLOAD_SUCCEEDED") ? "UPLOAD" : "COMPANION";
        return new PresenceEventRequest(session.sessionId(), "event-" + seq, seq, clock.instant(), type, page);
    }
    @Test void defaultOffAndServerOffNeverTouchModelOrQuota() {
        assertThat(service.open(7, new PresenceSessionRequest(null,null,null,null,null)).sessionId()).isNull();
        service = service(false, true);
        assertThat(open("BYOK", 3, false).mode()).isEqualTo("OFF");
        verifyNoInteractions(router, invoker, quota, usage);
    }
    @Test void realServerGateCannotBeBypassedByExplicitOptIn() {
        service = service(true, false);
        assertThatThrownBy(() -> open("BYOK",3,false)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(router, invoker, quota);
    }
    @Test void demoHasAllThreeActionsAndNeverUsesExternalDependencies() {
        var session = open("DEMO",3,false);
        assertThat(service.decide(7,event(session,1,"PAGE_ENTER")).action()).isEqualTo("SILENT");
        clock.advance(15);
        assertThat(service.decide(7,event(session,2,"PICTURE_OPENED")).emote()).isEqualTo("NOD");
        clock.advance(15);
        assertThat(service.decide(7,event(session,3,"UPLOAD_SUCCEEDED")).action()).isEqualTo("SPEAK");
        verifyNoInteractions(router, invoker, quota, usage, companions, messages);
    }
    @Test void muteDedupCooldownAndSessionBudgetGateBeforeCalls() {
        var muted = open("BYOK",2,true);
        assertThat(service.decide(7,event(muted,1,"PAGE_ENTER")).reason()).isEqualTo("MUTED");
        var session = open("BYOK",1,false);
        var e = event(session,1,"PAGE_ENTER");
        assertThat(service.decide(7,e).action()).isEqualTo("SPEAK");
        assertThat(service.decide(7,e).reason()).isEqualTo("DUPLICATE_EVENT");
        assertThat(service.decide(7,event(session,2,"PAGE_ENTER")).reason()).isEqualTo("SESSION_BUDGET");
        verify(invoker,times(1)).streamBounded(any(),anyList(),eq(160));
        verify(quota,times(1)).reserve(eq(7L),any(),eq(24));
    }
    @Test void staleFutureExpiredAndOutOfOrderNeverCall() {
        var session = open("BYOK",3,false);
        var stale = event(session,1,"PAGE_ENTER"); clock.advance(31);
        assertThat(service.decide(7,stale).reason()).isEqualTo("STALE_EVENT");
        var future = new PresenceEventRequest(session.sessionId(),"future",2L,clock.instant().plusSeconds(6),"PAGE_ENTER","COMPANION");
        assertThat(service.decide(7,future).reason()).isEqualTo("STALE_EVENT");
        assertThat(service.decide(7,event(session,3,"USER_MESSAGE")).reason()).isEqualTo("USER_MESSAGE_PRIORITY");
        assertThat(service.decide(7,event(session,2,"PAGE_ENTER")).reason()).isEqualTo("DUPLICATE_EVENT");
        clock.advance(900);
        assertThat(service.decide(7,event(session,4,"PAGE_ENTER")).reason()).isEqualTo("SESSION_EXPIRED");
        verifyNoInteractions(router, invoker, quota);
    }
    @Test void userMessageAlwaysSilencesAndStartsCooldown() {
        var session = open("BYOK",3,false);
        service.decide(7,event(session,1,"USER_MESSAGE"));
        assertThat(service.decide(7,event(session,2,"PAGE_ENTER")).reason()).isEqualTo("COOLDOWN");
        verifyNoInteractions(router, invoker, quota);
    }
    @Test void ownershipClosureAndReplacementAreIsolated() {
        var first = open("DEMO",3,false);
        assertThatThrownBy(() -> service.decide(8,event(first,1,"PAGE_ENTER"))).isInstanceOf(BusinessException.class);
        assertThat(service.close(8,first.sessionId())).isFalse();
        var second = open("DEMO",3,false);
        assertThatThrownBy(() -> service.decide(7,event(first,1,"PAGE_ENTER"))).isInstanceOf(BusinessException.class);
        assertThat(service.close(7,second.sessionId())).isTrue();
        assertThatThrownBy(() -> service.decide(7,event(second,1,"PAGE_ENTER"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.open(0,new PresenceSessionRequest(true,"DEMO",3,false,"LOW"))).isInstanceOf(BusinessException.class);
    }
    @Test void invalidInputsAreRejectedBeforeCalls() {
        var session = open("DEMO",3,false);
        for (String type : List.of("CLICK_PASSWORD", "<script>", ""))
            assertThatThrownBy(() -> service.decide(7,event(session,1,type))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.decide(7,new PresenceEventRequest(session.sessionId(),"ok",1L,clock.instant(),"PICTURE_OPENED","GALLERY")))
                .isInstanceOf(BusinessException.class);
        for (int budget : new int[]{0,13}) assertThatThrownBy(() -> open("DEMO",budget,false)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(router,invoker,quota);
    }
    @Test void boundedSingleCallReservesBeforeInvocationAndDoesNotUseUnboundedMethod() {
        var result = service.decide(7,event(open("BYOK",2,false),1,"PAGE_ENTER"));
        assertThat(result.remainingDecisions()).isEqualTo(1);
        var order = inOrder(quota,router,invoker);
        order.verify(quota).reserve(eq(7L),any(),eq(24));
        order.verify(router).decide(7);
        order.verify(invoker).streamBounded(eq(route),anyList(),eq(160));
        verify(invoker,never()).stream(any(),any());
    }
    @Test void platformRouteQuotaFailureAndProviderFailureNeverFallbackOrRetry() {
        when(router.decide(7)).thenReturn(ModelRouteDecision.platform());
        assertThat(service.decide(7,event(open("BYOK",3,false),1,"PAGE_ENTER")).reason()).isEqualTo("BYOK_REQUIRED");
        verifyNoInteractions(invoker);
        when(router.decide(7)).thenReturn(route);
        when(invoker.streamBounded(any(),anyList(),anyInt())).thenReturn(Flux.error(new IllegalStateException("secret")));
        assertThat(service.decide(7,event(open("BYOK",3,false),1,"PAGE_ENTER")).reason()).isEqualTo("UNAVAILABLE");
        verify(invoker,times(1)).streamBounded(any(),anyList(),anyInt());
        doThrow(new IllegalStateException("quota exhausted")).when(quota).reserve(anyLong(),any(),anyInt());
        assertThat(service.decide(7,event(open("BYOK",3,false),1,"PAGE_ENTER")).action()).isEqualTo("SILENT");
        verify(invoker,times(1)).streamBounded(any(),anyList(),anyInt());
    }

    @Test void safeInvocationErrorsAreDistinguishedWithoutExposingMessagesOrRetrying() {
        for (var entry : Map.of(ConnectivityResult.CREDENTIAL_REJECTED, "MODEL_CREDENTIAL_REJECTED",
                ConnectivityResult.UPSTREAM_TIMEOUT, "MODEL_TIMEOUT").entrySet()) {
            reset(invoker);
            when(invoker.streamBounded(any(), anyList(), anyInt())).thenReturn(Flux.error(
                    new ModelInvocationException(entry.getKey(), "fixture-secret-response")));
            var result = service.decide(7, event(open("BYOK", 1, false), 1, "PAGE_ENTER"));
            assertThat(result.reason()).isEqualTo(entry.getValue());
            assertThat(result.action()).isEqualTo("SILENT");
            assertThat(result.text()).isNull();
            assertThat(result.remainingDecisions()).isZero();
            assertThat(result.toString()).doesNotContain("fixture-secret-response");
            verify(invoker, times(1)).streamBounded(any(), anyList(), eq(160));
            verify(invoker, never()).stream(any(), any());
        }
    }

    @Test void emptyInvalidAndOversizedDecisionsHaveDifferentSafeReasons() {
        for (var entry : Map.of("", "EMPTY_OUTPUT", "not json", "INVALID_DECISION",
                "x".repeat(1025), "OUTPUT_LIMIT").entrySet()) {
            when(invoker.streamBounded(any(), anyList(), anyInt())).thenReturn(Flux.just(entry.getKey()));
            var result = service.decide(7, event(open("BYOK", 1, false), 1, "PAGE_ENTER"));
            assertThat(result.reason()).isEqualTo(entry.getValue());
            assertThat(result.action()).isEqualTo("SILENT");
            assertThat(result.text()).isNull();
        }
    }

    @Test void dailyBudgetFailureIsReportedBeforeRoutingAndNeverCallsModel() {
        doThrow(new BusinessException(com.li.lipicturecloud.exception.ErrorCode.FORBIDDEN_ERROR,
                "fixture-secret-quota")).when(quota).reserve(anyLong(), any(), anyInt());
        var result = service.decide(7, event(open("BYOK", 1, false), 1, "PAGE_ENTER"));
        assertThat(result.reason()).isEqualTo("DAILY_BUDGET");
        assertThat(result.text()).isNull();
        verifyNoInteractions(router, invoker, usage);
    }

    @Test void accountingFailureDoesNotMasqueradeAsModelFailureOrRetry() {
        doThrow(new IllegalStateException("fixture-secret-accounting"))
                .when(usage).recordSuccess(anyLong(), any(), any(), any(), anyString(), any());
        var result = service.decide(7, event(open("BYOK", 1, false), 1, "PAGE_ENTER"));
        assertThat(result.reason()).isEqualTo("ACCOUNTING_FAILED");
        assertThat(result.text()).isNull();
        verify(invoker, times(1)).streamBounded(any(), anyList(), eq(160));
        verify(invoker, never()).stream(any(), any());
    }
    @Test void malformedAndOverlongOutputsFailClosedAndSpendAttempt() {
        for (String raw : List.of("not json", "x".repeat(1025))) {
            when(invoker.streamBounded(any(),anyList(),anyInt())).thenReturn(Flux.just(raw));
            var result = service.decide(7,event(open("BYOK",1,false),1,"PAGE_ENTER"));
            assertThat(result.action()).isEqualTo("SILENT");
            assertThat(result.remainingDecisions()).isZero();
            assertThat(result.text()).isNull();
        }
        verify(quota,times(2)).reserve(anyLong(),any(),eq(24));
    }
    @Test void userMessageSupersedesInFlightResponseWithoutAnotherCall() throws Exception {
        var session = open("BYOK",3,false);
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        CountDownLatch entered = new CountDownLatch(1);
        when(invoker.streamBounded(any(),anyList(),anyInt())).thenAnswer(a -> { entered.countDown(); return sink.asFlux(); });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> service.decide(7,event(session,1,"PAGE_ENTER")));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            assertThat(service.decide(7,event(session,2,"USER_MESSAGE")).reason()).isEqualTo("USER_MESSAGE_PRIORITY");
            sink.tryEmitNext("{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"迟到的回复\"}");
            sink.tryEmitComplete();
            assertThat(pending.get(2,TimeUnit.SECONDS).reason()).isEqualTo("SUPERSEDED");
        }
        verify(invoker,times(1)).streamBounded(any(),anyList(),anyInt());
    }
    @Test void closeSupersedesInFlightResponse() throws Exception {
        var session = open("BYOK",3,false);
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        CountDownLatch entered = new CountDownLatch(1);
        when(invoker.streamBounded(any(),anyList(),anyInt())).thenAnswer(a -> { entered.countDown(); return sink.asFlux(); });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> service.decide(7,event(session,1,"PAGE_ENTER")));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            service.close(7,session.sessionId());
            sink.tryEmitNext("{\"action\":\"EMOTE\",\"emote\":\"WAVE\",\"text\":null}"); sink.tryEmitComplete();
            assertThat(pending.get(2,TimeUnit.SECONDS).action()).isEqualTo("SILENT");
        }
    }
    @Test void stalledModelTimesOutWithoutRetryAndKeepsSpentBudget() {
        when(invoker.streamBounded(any(),anyList(),anyInt())).thenReturn(Flux.never());
        var result = service.decide(7,event(open("BYOK",1,false),1,"PAGE_ENTER"));
        assertThat(result.reason()).isEqualTo("MODEL_TIMEOUT");
        assertThat(result.remainingDecisions()).isZero();
        verify(invoker,times(1)).streamBounded(any(),anyList(),anyInt());
        verify(quota,times(1)).reserve(anyLong(),any(),eq(24));
    }
    @Test void recentOrdinaryChatHasPriorityEvenWithoutClientNotification() {
        var companion = Companion.awaken(7,CompanionBalance.v1()).persistedAs(11);
        when(companions.findByOwnerId(7)).thenReturn(Optional.of(companion));
        when(messages.findRecent(11,1)).thenReturn(List.of(CompanionChatMessage.user(11,7,"hello",clock.instant()).withId(31)));
        var result = service.decide(7,event(open("BYOK",3,false),1,"PAGE_ENTER"));
        assertThat(result.reason()).isEqualTo("USER_MESSAGE_PRIORITY");
        verifyNoInteractions(router,invoker,quota);
    }
    @Test void ordinaryChatDuringRoutingPreventsProviderDispatch() throws Exception {
        var companion = Companion.awaken(7,CompanionBalance.v1()).persistedAs(11);
        when(companions.findByOwnerId(7)).thenReturn(Optional.of(companion));
        var previous = CompanionChatMessage.user(11,7,"before",clock.instant().minusSeconds(60)).withId(31);
        var next = CompanionChatMessage.user(11,7,"new chat",clock.instant()).withId(32);
        var latest = new java.util.concurrent.atomic.AtomicReference<>(previous);
        when(messages.findRecent(11,1)).thenAnswer(a -> List.of(latest.get()));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(router.decide(7)).thenAnswer(a -> { entered.countDown(); release.await(2,TimeUnit.SECONDS); return route; });
        var session = open("BYOK",3,false);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> service.decide(7,event(session,1,"PAGE_ENTER")));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            latest.set(next); release.countDown();
            assertThat(pending.get(2,TimeUnit.SECONDS).reason()).isEqualTo("USER_MESSAGE_PRIORITY");
        } finally { release.countDown(); }
        verifyNoInteractions(invoker);
    }
    @Test void eventExpiringDuringRoutingPreventsProviderDispatch() {
        when(router.decide(7)).thenAnswer(a -> { clock.advance(31); return route; });
        var session = open("BYOK",3,false);
        assertThat(service.decide(7,event(session,1,"PAGE_ENTER")).reason()).isEqualTo("SUPERSEDED");
        verifyNoInteractions(invoker);
    }
    @Test void statusIsReadOnlySafeAndGated() throws Exception {
        service = service(false, true);
        assertThat(service.status(7).reason()).isEqualTo("DISABLED");
        service = service(true, false);
        assertThat(service.status(7).reason()).isEqualTo("REAL_DISABLED");
        verifyNoInteractions(router, invoker, quota, usage, companions, messages);
        service = service(true, true);
        assertThat(service.status(7).reason()).isEqualTo("BYOK_REQUIRED");
        when(router.inspectCurrentByok(7)).thenThrow(new IllegalStateException("secret endpoint credential"));
        assertThat(service.status(7)).isEqualTo(new PresenceAvailabilityView(true,true,false,"UNAVAILABLE",null));
        doReturn(Optional.of(route.connection())).when(router).inspectCurrentByok(7);
        var status = service.status(7);
        assertThat(status.available()).isTrue();
        assertThat(status.connection().provider()).isEqualTo(ModelProvider.DEEPSEEK);
        assertThat(status.connection().modelCode()).isEqualTo("deepseek-chat");
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(status);
        assertThat(json).doesNotContain("apiKey", "credential", "endpoint", "fixture-only", "subjectId");
        assertThatThrownBy(() -> service.status(0)).isInstanceOf(BusinessException.class);
        verify(router, never()).decide(anyLong());
        verifyNoInteractions(invoker, quota, usage, companions, messages);
    }

    @Test void routeChangedAfterStatusIsRecheckedAtDispatchWithoutFallback() {
        when(router.inspectCurrentByok(7)).thenReturn(Optional.of(route.connection()));
        assertThat(service.status(7).available()).isTrue();
        var session = open("BYOK", 2, false);
        when(router.decide(7)).thenReturn(ModelRouteDecision.platform());
        assertThat(service.decide(7,event(session,1,"PAGE_ENTER")).reason()).isEqualTo("BYOK_REQUIRED");
        verifyNoInteractions(invoker);
        when(router.decide(7)).thenThrow(new IllegalStateException("connection deleted"));
        clock.advance(15);
        assertThat(service.decide(7,event(session,2,"PAGE_ENTER")).reason()).isEqualTo("ROUTE_UNAVAILABLE");
        verifyNoInteractions(invoker);
    }

    @Test void explicitManualDebugFieldsAreAcceptedAndRemainServerGated() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var request = mapper.readValue("{\"enabled\":true,\"mode\":\"BYOK\",\"maxDecisions\":6,\"scope\":\"REHEARSAL\",\"cooldownSeconds\":2}", PresenceSessionRequest.class);
        assertThatThrownBy(() -> service.open(7, request)).isInstanceOf(BusinessException.class);
    }


    void enableDebug(int dailyLimit) {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "rehearsalEnabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "rehearsalDailyLimit", dailyLimit);
    }
    PresenceSessionView debug(int cooldown) {
        return service.open(7, new PresenceSessionRequest(true, "BYOK", 6, false, "LOW", "REHEARSAL", cooldown));
    }
    @Test void manualSlotIsIndependentAndOnlyServerConfigurationRaisesSharedDailyLimit() {
        enableDebug(100);
        var site = open("BYOK", 3, false);
        var manual = debug(5);
        assertThat(service.decide(7, event(site, 1, "PAGE_ENTER")).dailyLimit()).isEqualTo(24);
        var result = service.decide(7, event(manual, 1, "PAGE_ENTER"));
        assertThat(result.dailyLimit()).isEqualTo(100);
        assertThat(result.modelDispatched()).isTrue();
        assertThat(result.attemptConsumed()).isTrue();
        assertThat(result.stage()).isEqualTo("DONE");
        assertThat(service.close(7, manual.sessionId())).isTrue();
        clock.advance(15);
        assertThat(service.decide(7, event(site, 2, "PAGE_ENTER")).reason()).isEqualTo("DECIDED");
        verify(quota, times(2)).reserve(eq(7L), any(), eq(24));
        verify(quota).reserve(eq(7L), any(), eq(100));
    }
    @Test void manualCooldownHasAuthoritativeCountdownWithoutSpendingRejectedAttempt() {
        enableDebug(24);
        var manual = debug(2);
        service.decide(7, event(manual, 1, "PAGE_ENTER"));
        var blocked = service.decide(7, event(manual, 2, "PAGE_ENTER"));
        assertThat(blocked.reason()).isEqualTo("COOLDOWN");
        assertThat(blocked.modelDispatched()).isFalse();
        assertThat(blocked.attemptConsumed()).isFalse();
        assertThat(blocked.remainingDecisions()).isEqualTo(5);
        assertThat(blocked.nextAllowedAt()).isEqualTo(clock.instant().plusSeconds(2));
        clock.advance(2);
        assertThat(service.decide(7, event(manual, 3, "PAGE_ENTER")).reason()).isEqualTo("DECIDED");
        verify(quota, times(2)).reserve(eq(7L), any(), eq(24));
    }
    @Test void quotaRejectionRestoresUnconsumedSessionSlotButEmptyOutputKeepsRealAttempt() {
        enableDebug(24);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN_ERROR)).when(quota).reserve(anyLong(), any(), anyInt());
        var blocked = service.decide(7, event(debug(5), 1, "PAGE_ENTER"));
        assertThat(blocked.remainingDecisions()).isEqualTo(6);
        assertThat(blocked.attemptConsumed()).isFalse();
        assertThat(blocked.modelDispatched()).isFalse();
        reset(quota);
        when(invoker.streamBounded(any(), anyList(), anyInt())).thenReturn(Flux.empty());
        var failed = service.decide(7, event(debug(5), 1, "PAGE_ENTER"));
        assertThat(failed.reason()).isEqualTo("EMPTY_OUTPUT");
        assertThat(failed.stage()).isEqualTo("PARSE");
        assertThat(failed.outputChars()).isZero();
        assertThat(failed.attemptConsumed()).isTrue();
        assertThat(failed.modelDispatched()).isTrue();
        assertThat(failed.remainingDecisions()).isEqualTo(5);
    }
    @Test void debugBudgetReadKeepsExistingUsageWithoutReservingOrChangingProvider() {
        enableDebug(100);
        when(quota.used(eq(7L), any())).thenReturn(6);
        assertThat(service.status(7).rehearsalDailyRemaining()).isEqualTo(94);
        verify(quota, never()).reserve(anyLong(), any(), anyInt());
        verifyNoInteractions(invoker, usage);
    }
    @Test void manualParameterBoundsAndSiteOverridesFailClosed() {
        enableDebug(24);
        for (int cooldown : new int[]{0, 1, 31, 100})
            assertThatThrownBy(() -> debug(cooldown)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.open(7, new PresenceSessionRequest(true, "DEMO", 6, false, "LOW", "SITE", 2))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.open(7, new PresenceSessionRequest(true, "DEMO", 100, false, "LOW", "REHEARSAL", 5))).isInstanceOf(BusinessException.class);
        enableDebug(101);
        assertThatThrownBy(() -> debug(5)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(invoker, quota);
    }


    @Test void replacingManualSlotCannotOverlapOldPaidCallOrAnotherScope() throws Exception {
        enableDebug(24);
        var manual = debug(2);
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        CountDownLatch entered = new CountDownLatch(1);
        when(invoker.streamBounded(any(), anyList(), anyInt())).thenAnswer(a -> { entered.countDown(); return sink.asFlux(); });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> service.decide(7, event(manual, 1, "PAGE_ENTER")));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            service.close(7, manual.sessionId());
            var replacement = debug(2);
            assertThat(service.decide(7, event(replacement, 1, "PAGE_ENTER")).reason()).isEqualTo("BUSY");
            var site = open("BYOK", 3, false);
            assertThat(service.decide(7, event(site, 1, "PAGE_ENTER")).reason()).isEqualTo("BUSY");
            sink.tryEmitNext("{\"action\":\"SILENT\",\"emote\":null,\"text\":null}"); sink.tryEmitComplete();
            assertThat(pending.get(2, TimeUnit.SECONDS).reason()).isEqualTo("SUPERSEDED");
            when(invoker.streamBounded(any(), anyList(), anyInt())).thenReturn(Flux.just(
                    "{\"action\":\"SILENT\",\"emote\":null,\"text\":null}"));
            assertThat(service.decide(7, event(replacement, 2, "PAGE_ENTER")).reason()).isEqualTo("DECIDED");
        }
        verify(quota, times(2)).reserve(eq(7L), any(), eq(24));
    }

    @Test void manualTraceUsesActualMessagesAndRawSilentWhileSiteAndDemoOmitIt() throws Exception {
        enableDebug(100);
        String raw = "{\"action\":\"SILENT\",\"emote\":null,\"text\":null}";
        when(invoker.streamBounded(any(), anyList(), eq(160))).thenReturn(Flux.just(raw));
        var manual = debug(2);
        var result = service.decide(7, event(manual, 1, "PICTURE_OPENED"));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var data = mapper.valueToTree(result).path("debug");
        assertThat(data.isObject()).as("manual request must return its actual trace").isTrue();
        assertThat(data.path("rawBody").asText()).isEqualTo(raw);
        assertThat(data.path("parsedDecision").path("action").asText()).isEqualTo("SILENT");
        assertThat(data.path("messages").get(1).path("content").asText()).isEqualTo("eventType=PICTURE_OPENED;page=PICTURE_DETAIL");
        assertThat(data.path("parameters").path("maxTokens").asInt()).isEqualTo(160);
        assertThat(data.toString()).doesNotContain("fixture-only", "api.deepseek.com", "Authorization");
        assertThat(mapper.valueToTree(service.decide(7, event(open("BYOK", 2, false), 1, "PAGE_ENTER"))).path("debug").isNull()).isTrue();
        var demo = service.open(7, new PresenceSessionRequest(true, "DEMO", 2, false, "LOW", "REHEARSAL", 2));
        assertThat(mapper.valueToTree(service.decide(7, event(demo, 1, "PAGE_ENTER"))).path("debug").isNull()).isTrue();
    }
    @Test void emptyManualTraceIsEmptyNotSyntheticSilentAndCannotBeReadByAnotherOwner() {
        enableDebug(100); when(invoker.streamBounded(any(), anyList(), eq(160))).thenReturn(Flux.empty());
        var session = debug(2); var request = event(session, 1, "PAGE_ENTER");
        assertThatThrownBy(() -> service.decide(8, request)).isInstanceOf(BusinessException.class);
        var result = service.decide(7, request);
        var data = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().valueToTree(result).path("debug");
        assertThat(data.isObject()).isTrue(); assertThat(data.path("rawBody").asText()).isEmpty();
        assertThat(data.path("parsedDecision").isNull()).isTrue(); assertThat(result.reason()).isEqualTo("EMPTY_OUTPUT");
        assertThat(data.path("rawBodyComplete").asBoolean()).isTrue();
    }

    @Test void manualPartialBodyRemainsIncompleteAndOutputCapStillRejectsWithoutParsingPrefix() {
        enableDebug(100); var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        when(invoker.streamBounded(any(), anyList(), eq(160))).thenReturn(Flux.concat(Flux.just("PARTIAL_BODY_SENTINEL"), Flux.error(
                new ModelInvocationException(ConnectivityResult.UPSTREAM_ERROR, "safe failure", null, ModelInvocationException.FailureKind.PROTOCOL, null))));
        var result = service.decide(7, event(debug(2), 1, "PAGE_ENTER"));
        var data = mapper.valueToTree(result).path("debug");
        assertThat(data.path("rawBody").asText()).isEqualTo("PARTIAL_BODY_SENTINEL");
        assertThat(data.path("rawBodyComplete").asBoolean()).isFalse(); assertThat(data.path("parsedDecision").isNull()).isTrue();
        assertThat(result.reason()).isEqualTo("MODEL_STREAM_INVALID"); assertThat(result.attemptConsumed()).isTrue();
        when(invoker.streamBounded(any(), anyList(), eq(160))).thenReturn(Flux.just("x".repeat(1025)));
        result = service.decide(7, event(debug(2), 1, "PAGE_ENTER")); data = mapper.valueToTree(result).path("debug");
        assertThat(result.reason()).isEqualTo("OUTPUT_LIMIT"); assertThat(data.path("rawBody").asText()).hasSize(1024);
        assertThat(data.path("rawBodyTruncated").asBoolean()).isTrue(); assertThat(data.path("parsedDecision").isNull()).isTrue();
    }
    @Test void manualRawAndParsedTextRedactCredentialEvenIfProviderJsonEscapesIt() {
        enableDebug(100); String raw = "{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"\\u0066ixture-only\"}";
        when(invoker.streamBounded(any(), anyList(), eq(160))).thenReturn(Flux.just(raw));
        var result = service.decide(7, event(debug(2), 1, "PAGE_ENTER"));
        var data = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().valueToTree(result).path("debug");
        assertThat(data.path("rawBody").asText()).doesNotContain("fixture-only", "\\u0066ixture-only").contains("REDACTED_CREDENTIAL");
        assertThat(data.path("parsedDecision").path("text").asText()).isEqualTo("[REDACTED_CREDENTIAL]");
        assertThat(result.text()).isEqualTo("[REDACTED_CREDENTIAL]");
        assertThat(result.debug().toString()).doesNotContain("REDACTED_CREDENTIAL", "SPEAK");
        assertThat(data.path("redactedFields").get(0).asText()).isEqualTo("CREDENTIAL");
    }
    @Test void mixedPlainAndEscapedCredentialAreBothRedacted() {
        enableDebug(100); String raw = "{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"fixture-only + \\u0066ixture-only\"}";
        when(invoker.streamBounded(any(), anyList(), eq(160))).thenReturn(Flux.just(raw));
        var result = service.decide(7, event(debug(2), 1, "PAGE_ENTER"));
        assertThat(result.debug().rawBody()).doesNotContain("fixture-only", "\\u0066ixture-only");
        assertThat(result.debug().rawBody()).contains("[REDACTED_CREDENTIAL] + [REDACTED_CREDENTIAL]");
    }
    @Test void deeplyEscapedAndPlainCredentialAreBothRedacted() throws Exception {
        enableDebug(100);
        String text = "fixture-only + " + "\\".repeat(8) + "u0066ixture-only";
        String raw = "{\"action\":\"SPEAK\",\"emote\":null,\"text\":"
                + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(text) + "}";
        when(invoker.streamBounded(any(), anyList(), eq(160))).thenReturn(Flux.just(raw));
        var result = service.decide(7, event(debug(2), 1, "PAGE_ENTER"));
        assertThat(result.debug().rawBody()).doesNotContain("ixture-only", "u0066");
        assertThat(result.debug().rawBody()).contains("[REDACTED_CREDENTIAL] + [REDACTED_CREDENTIAL]");
        assertThat(result.debug().parsedDecision().text()).isEqualTo("[REDACTED_CREDENTIAL] + [REDACTED_CREDENTIAL]");
        assertThat(result.text()).isEqualTo("[REDACTED_CREDENTIAL] + [REDACTED_CREDENTIAL]");
        assertThat(result.debug().redactedFields()).containsExactly("CREDENTIAL");
    }
    @Test void gatedManualRequestDoesNotFabricateModelMessagesOrBody() {
        enableDebug(100); var session = debug(2); service.decide(7, event(session, 1, "PAGE_ENTER"));
        var gated = service.decide(7, event(session, 2, "PAGE_ENTER"));
        assertThat(gated.reason()).isEqualTo("COOLDOWN"); assertThat(gated.modelDispatched()).isFalse();
        assertThat(gated.debug().messages()).isNull(); assertThat(gated.debug().parameters()).isNull(); assertThat(gated.debug().rawBody()).isNull(); assertThat(gated.debug().parsedDecision()).isNull();
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-09T15:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now,zone); }
        public Instant instant() { return now; }
        void advance(long seconds) { now = now.plusSeconds(seconds); }
    }
}
