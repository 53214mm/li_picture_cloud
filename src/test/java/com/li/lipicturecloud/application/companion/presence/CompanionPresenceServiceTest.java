package com.li.lipicturecloud.application.companion.presence;

import com.li.lipicturecloud.application.airuntime.*;
import com.li.lipicturecloud.domain.airuntime.*;
import com.li.lipicturecloud.domain.companion.*;
import com.li.lipicturecloud.exception.BusinessException;
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
        assertThat(result.reason()).isEqualTo("UNAVAILABLE");
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
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-09T15:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now,zone); }
        public Instant instant() { return now; }
        void advance(long seconds) { now = now.plusSeconds(seconds); }
    }
}
