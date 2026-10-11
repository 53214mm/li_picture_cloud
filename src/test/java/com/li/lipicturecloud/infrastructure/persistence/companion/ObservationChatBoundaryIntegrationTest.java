package com.li.lipicturecloud.infrastructure.persistence.companion;
import com.li.lipicturecloud.application.companion.*;
import com.li.lipicturecloud.application.companion.observation.*;
import com.li.lipicturecloud.domain.companion.*;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test")
class ObservationChatBoundaryIntegrationTest {
 @Autowired CompanionRepository companions;@Autowired CompanionChatMessageRepository messages;
 @Autowired ChatSessionBoundaryRepository boundaries;@Autowired CompanionChatWrites writes;
 @Autowired CompanionChatService chat;@Autowired CompanionChatModeService modes;
 @Autowired ObservationChatActivity activity;@Autowired ObservationContext context;
 @Test void durableEmptyChatAbaAndLoginHaveStrictlyIncreasingEpoch(){
  long owner=9451;long id=companions.createIfAbsent(owner,CompanionBalance.v1()).id();long before=boundaries.chatEpoch(owner);
  messages.append(CompanionChatMessage.user(id,owner,"fixture user",Instant.now()));long afterUser=boundaries.chatEpoch(owner);assertThat(afterUser).isGreaterThan(before);
  writes.clear(id,owner);long afterClear=boundaries.chatEpoch(owner);assertThat(afterClear).isGreaterThan(afterUser);assertThat(messages.findRecent(id,6)).isEmpty();
  boundaries.recordLogin(owner,Instant.now());assertThat(boundaries.chatEpoch(owner)).isGreaterThan(afterClear);
 }
 @Test void actualSpringNormalChatBlocksAnotherTabObservationUntilTerminalWithoutAnyModel(){
  long owner=9452;companions.createIfAbsent(owner,CompanionBalance.v1());modes.choose(owner,CompanionChatMode.DEMO);
  var stream=chat.chat(AuthorizationSubject.user(owner),"isolated DEMO fixture");assertThat(activity.busy(owner)).isTrue();
  assertThatThrownBy(()->context.prepare(owner,new ObservationContracts.PageInput("COMPANION",List.of(),List.of()),Instant.now())).hasMessage("CHAT_BUSY");
  stream.blockLast();assertThat(activity.busy(owner)).isFalse();
  assertThat(context.prepare(owner,new ObservationContracts.PageInput("COMPANION",List.of(),List.of()),Instant.now()).chat()).isNotEmpty();
 }
}
