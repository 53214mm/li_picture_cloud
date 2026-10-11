package com.li.lipicturecloud.application.companion.observation;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ObservationChatActivityTest {
 @Test void subscribedChatNeverExpiresWhileAnUnsubscribedOrphanDoes() {
  final Instant[] now={Instant.parse("2026-10-11T00:00:00Z")};
  Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now[0];}};
  var activity=new ObservationChatActivity(clock);var live=activity.begin(1);live.subscribed();activity.begin(2);
  now[0]=now[0].plusSeconds(3600);assertThat(activity.busy(1)).isTrue();assertThat(activity.busy(2)).isFalse();live.close();assertThat(activity.busy(1)).isFalse();
 }
 @Test void chatBeginsBeforeReturnedStreamAndEveryTerminalClearsOwnerBusy() throws Throwable {
  var activity=new ObservationChatActivity(Clock.systemUTC());var aspect=new ObservationChatPriorityAspect(activity);
  var call=mock(ProceedingJoinPoint.class);when(call.proceed()).thenReturn(Flux.never());
  @SuppressWarnings("unchecked") Flux<String> stream=(Flux<String>)aspect.around(call,AuthorizationSubject.user(1L));
  assertThat(activity.busy(1)).isTrue();assertThat(activity.busy(2)).isFalse();var subscription=stream.subscribe();subscription.dispose();assertThat(activity.busy(1)).isFalse();
  when(call.proceed()).thenReturn(Flux.just("fixture"));stream=(Flux<String>)aspect.around(call,AuthorizationSubject.user(1L));stream.blockLast();assertThat(activity.busy(1)).isFalse();
  when(call.proceed()).thenThrow(new IllegalStateException("fixture failure"));assertThatThrownBy(()->aspect.around(call,AuthorizationSubject.user(1L))).isInstanceOf(IllegalStateException.class);assertThat(activity.busy(1)).isFalse();
 }
}
