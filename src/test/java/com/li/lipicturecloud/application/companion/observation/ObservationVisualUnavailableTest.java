package com.li.lipicturecloud.application.companion.observation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.*;
import com.li.lipicturecloud.application.companion.presence.PresenceQuotaGuard;
import com.li.lipicturecloud.domain.airuntime.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.net.URI;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ObservationVisualUnavailableTest {
 @Test void unfinishedRealCaptureCannotOpenSyntheticVisualSessionOrCallProvider(){
  var language=mock(LanguageRouter.class);var vision=mock(VisionRouter.class);var visual=mock(ObservationVisionClient.class);var quota=mock(ObservationQuota.class);
  var text=ModelConnection.restore(3L,1,ModelProvider.OPENAI,"fixture",URI.create("https://fixture.invalid/v1"),"fixture",4L,true,1);
  when(language.inspectCurrentByok(1)).thenReturn(Optional.of(text));when(vision.inspectCurrentByok(1)).thenReturn(Optional.of(text));
  when(quota.status(anyLong(),any())).thenReturn(new ObservationQuota.Counts(0,0,null));
  var service=new CompanionObservationService(language,vision,mock(LanguageModelInvoker.class),visual,mock(ObservationContext.class),quota,mock(PresenceQuotaGuard.class),mock(ModelUsageService.class),new ObjectMapper(),Clock.systemUTC(),true);
  assertThat(service.status(1).visionReceiver()).isNull();
  var input=new ObservationContracts.OpenInput(500,100,"NORMAL",true,service.status(1).textReceiver().token(),"any");
  assertThatThrownBy(()->service.open(1,input)).hasMessage("REAL_VISUAL_CAPTURE_UNAVAILABLE");
  assertThat(service.preview(1,new ObservationContracts.PageInput("GALLERY",List.of(101L),List.of(new ObservationContracts.Rect(1,1,100,100)))).preview()).isNull();
  verifyNoInteractions(visual);verify(vision,never()).decide(anyLong());
 }
}
