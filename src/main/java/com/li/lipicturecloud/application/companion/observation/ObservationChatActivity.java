package com.li.lipicturecloud.application.companion.observation;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** App-instance owner activity shared by all tabs; restart also ends all old chat streams. No content. */
@Component
public class ObservationChatActivity {
 private final Clock clock;private final Map<Long,Map<String,Ticket>> active=new ConcurrentHashMap<>();
 public ObservationChatActivity(Clock clock){this.clock=clock;}
 public Ticket begin(long owner){
  String token=UUID.randomUUID().toString();
  var ticket=new Ticket(owner,token,clock.instant().plusSeconds(1800));
  active.compute(owner,(id,rows)->{var next=rows==null?new ConcurrentHashMap<String,Ticket>():rows;next.put(token,ticket);return next;});
  return ticket;
 }
 public final class Ticket implements AutoCloseable {
  private final long owner;private final String token;private final Instant orphanDeadline;
  private final AtomicBoolean closed=new AtomicBoolean();private volatile boolean subscribed;
  private Ticket(long owner,String token,Instant orphanDeadline){this.owner=owner;this.token=token;this.orphanDeadline=orphanDeadline;}
  public void subscribed(){subscribed=true;}
  private boolean orphanExpired(){return !subscribed && !clock.instant().isBefore(orphanDeadline);}
  public void close(){if(closed.compareAndSet(false,true))active.computeIfPresent(owner,(id,rows)->{rows.remove(token);return rows.isEmpty()?null:rows;});}
 }
 public boolean busy(long owner){
  var rows=active.computeIfPresent(owner,(id,current)->{current.entrySet().removeIf(entry->entry.getValue().orphanExpired());return current.isEmpty()?null:current;});
  return rows!=null && !rows.isEmpty();
 }
}
