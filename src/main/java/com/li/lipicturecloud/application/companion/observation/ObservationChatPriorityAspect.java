package com.li.lipicturecloud.application.companion.observation;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

/** Marks ordinary chat lifecycle only; leaves its model route, prompt, quota and stream operators intact. */
@Aspect @Component @Order(Ordered.HIGHEST_PRECEDENCE+50)
public class ObservationChatPriorityAspect {
 private final ObservationChatActivity activity;
 public ObservationChatPriorityAspect(ObservationChatActivity activity){this.activity=activity;}
 @Around("execution(* com.li.lipicturecloud.application.companion.CompanionChatService.chat(..)) && args(subject,..)")
 public Object around(ProceedingJoinPoint call,AuthorizationSubject subject) throws Throwable {
  var ticket=activity.begin(subject.userId());
  try{
   @SuppressWarnings("unchecked") Flux<String> stream=(Flux<String>)call.proceed();
   return stream.doOnSubscribe(subscription->ticket.subscribed()).doFinally(signal->close(ticket));
  }catch(Throwable failed){close(ticket);throw failed;}
 }
 private static void close(AutoCloseable ticket){try{ticket.close();}catch(Exception ignored){/* idempotent local close */}}
}
