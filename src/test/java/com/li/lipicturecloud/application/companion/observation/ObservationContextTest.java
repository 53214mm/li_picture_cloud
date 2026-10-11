package com.li.lipicturecloud.application.companion.observation;
import com.li.lipicturecloud.application.companion.*;
import com.li.lipicturecloud.domain.companion.*;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.service.PictureService;
import com.li.lipicturecloud.model.entity.Picture;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ObservationContextTest {
 @Test void chatStartingInsideSlowContextValidationFailsFinalBusyRecheck(){
  var at=Instant.now();var activity=new ObservationChatActivity(Clock.systemUTC());var companions=mock(CompanionRepository.class);var companion=mock(Companion.class);when(companions.findByOwnerId(1)).thenReturn(Optional.of(companion));
  var memories=mock(CompanionMemoryService.class);when(memories.confirmedMemoriesUsableBy(eq(companion),any(),eq(100))).thenAnswer(call->{activity.begin(1);return List.of();});
  var context=new ObservationContext(mock(PictureService.class),mock(SpaceAuthorizationAccessService.class),companions,mock(CompanionChatMessageRepository.class),mock(CompanionChatSessions.class),memories,mock(CompanionMemoryRecall.class),mock(ChatSessionBoundaryRepository.class),activity);
  assertThat(context.current(1,new ObservationContracts.Snapshot("COMPANION",List.of(),List.of(),List.of(),0,at))).isFalse();
 }
 @Test void confirmedOwnedRelatedMemoryUsesNoVectorAndDeniedVisiblePictureCannotReachSnapshot(){
  var now=Instant.parse("2026-10-11T12:00:00Z");var pictures=mock(PictureService.class);var auth=mock(SpaceAuthorizationAccessService.class);
  var companions=mock(CompanionRepository.class);var messages=mock(CompanionChatMessageRepository.class);var sessions=mock(CompanionChatSessions.class);
  var memories=mock(CompanionMemoryService.class);var boundaries=mock(ChatSessionBoundaryRepository.class);var index=mock(CompanionMemoryIndex.class);
  var companion=mock(Companion.class);when(companion.id()).thenReturn(11L);when(companion.ownerId()).thenReturn(1L);when(companions.findByOwnerId(1)).thenReturn(Optional.of(companion));
  when(messages.findRecent(11,6)).thenReturn(List.of());when(sessions.current(eq(1L),anyList(),eq(now))).thenReturn(new CompanionChatSessions.Session(List.of(),now,null));
  var memory=CompanionMemory.restore(1L,11,1,101L,5,MemorySourceType.VISUAL,"骑士站在城墙前","骑士站在城墙前",BigDecimal.ONE,MemoryStatus.CONFIRMED,null,1,now,now);
  when(memories.confirmedMemoriesUsableBy(eq(companion),any(),eq(30))).thenReturn(List.of(memory));
  var p=new Picture();p.setId(101L);p.setName("骑士");p.setUpdateTime(Date.from(now));when(pictures.getById(101L)).thenReturn(p);
  var activity=new ObservationChatActivity(Clock.fixed(now,ZoneOffset.UTC));var recall=new CompanionMemoryRecall(index,ids->Map.of(),.45);
  var context=new ObservationContext(pictures,auth,companions,messages,sessions,memories,recall,boundaries,activity);
  var snapshot=context.prepare(1,new ObservationContracts.PageInput("GALLERY",List.of(101L),List.of()),now);
  assertThat(snapshot.memories()).extracting(ObservationContracts.MemoryLine::id).containsExactly(1L);verifyNoInteractions(index);
  doThrow(new IllegalStateException("denied")).when(auth).checkForUser("picture:view",101L,1L);
  assertThatThrownBy(()->context.prepare(1,new ObservationContracts.PageInput("GALLERY",List.of(101L),List.of()),now)).hasMessage("denied");
 }
 @Test void chatAlreadyActiveBeforePreparationFailsBeforeAnyDataRead(){
  var now=Instant.now();var activity=new ObservationChatActivity(Clock.systemUTC());activity.begin(1);
  var pictures=mock(PictureService.class);var auth=mock(SpaceAuthorizationAccessService.class);var companions=mock(CompanionRepository.class);
  var messages=mock(CompanionChatMessageRepository.class);var sessions=mock(CompanionChatSessions.class);var memories=mock(CompanionMemoryService.class);
  var recall=mock(CompanionMemoryRecall.class);var boundaries=mock(ChatSessionBoundaryRepository.class);
  var context=new ObservationContext(pictures,auth,companions,messages,sessions,memories,recall,boundaries,activity);
  assertThatThrownBy(()->context.prepare(1,new ObservationContracts.PageInput("COMPANION",List.of(),List.of()),now)).hasMessage("CHAT_BUSY");
  verifyNoInteractions(pictures,auth,companions,messages,sessions,memories,recall,boundaries);
 }
}
