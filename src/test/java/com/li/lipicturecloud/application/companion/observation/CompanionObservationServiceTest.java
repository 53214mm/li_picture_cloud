package com.li.lipicturecloud.application.companion.observation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.*;
import com.li.lipicturecloud.application.companion.presence.PresenceQuotaGuard;
import com.li.lipicturecloud.domain.airuntime.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.net.URI;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CompanionObservationServiceTest {
 RealObservationRegion region=mock(RealObservationRegion.class);
 LanguageRouter language=mock(LanguageRouter.class);VisionRouter vision=mock(VisionRouter.class);
 LanguageModelInvoker invoker=mock(LanguageModelInvoker.class);ObservationContext context=mock(ObservationContext.class);
 ObservationQuota quota=mock(ObservationQuota.class);ObservationVisionClient visual=mock(ObservationVisionClient.class);
 ModelUsageService usage=mock(ModelUsageService.class);PresenceQuotaGuard manual=mock(PresenceQuotaGuard.class);
 Instant at=Instant.parse("2026-10-11T12:00:00Z");CompanionObservationService service;
 ModelConnection connection=ModelConnection.restore(3L,1,ModelProvider.OPENAI,"local fixture",URI.create("https://fixture.invalid/v1"),"fixture",4L,true,1);
 @BeforeEach void setup(){
  when(region.enabled()).thenReturn(true);when(region.current(anyLong(),any())).thenReturn(true);
  try{when(region.capture(anyLong(),any(),any())).thenReturn(new RealObservationRegion.Preview(RealObservationRegionTest.photo(),"real-fixture","REAL_OBSERVATION_REGION","Allowed title",Map.of(),List.of()));}catch(Exception failed){throw new AssertionError(failed);}
  when(language.inspectCurrentByok(1)).thenReturn(Optional.of(connection));when(vision.inspectCurrentByok(1)).thenReturn(Optional.empty());
  when(language.decide(1)).thenReturn(ModelRouteDecision.byok(connection,"fixture-secret"));
  when(quota.status(anyLong(),any())).thenReturn(new ObservationQuota.Counts(0,0,null));
  when(quota.claim(anyLong(),anyString(),any())).thenReturn(true);when(quota.reserve(anyLong(),anyString(),anyBoolean(),any())).thenReturn(true);
  when(context.prepare(eq(1L),any(),any())).thenReturn(new ObservationContracts.Snapshot("COMPANION",List.of(),List.of(),List.of(),0,at));
  when(context.current(eq(1L),any())).thenReturn(true);
  service=new CompanionObservationService(language,vision,invoker,visual,context,quota,manual,usage,new ObjectMapper().findAndRegisterModules(),Clock.fixed(at,ZoneOffset.UTC),true,region);
 }
 ObservationContracts.OpenInput grant(int text,int image,String intensity,boolean auto){
  var status=service.status(1);return new ObservationContracts.OpenInput(text,image,intensity,auto,status.textReceiver().token(),status.visionReceiver()==null?null:status.visionReceiver().token());
 }
 @Test void revokedWhileFinalRouteCheckBlocksCannotDispatchOrRefund() {
  var open=service.open(1,grant(2,0,"NORMAL",true));
  var prepared=service.prepare(1,new CompanionObservationService.PrepareInput(open.sessionId(),new ObservationContracts.PageInput("COMPANION",List.of(),List.of())));
  doAnswer(call->{when(language.inspectCurrentByok(1)).thenAnswer(route->{service.close(1,open.sessionId());return Optional.of(connection);});return true;}).when(quota).reserve(eq(1L),eq(open.sessionId()),eq(false),any());
  var result=service.observe(1,new CompanionObservationService.ObserveInput(open.sessionId(),prepared.snapshotId(),1));
  assertThat(result.modelDispatched()).isFalse();assertThat(result.reason()).isEqualTo("REVOKED");assertThat(result.textRemaining()).isEqualTo(1);verifyNoInteractions(invoker,visual);
 }
 @Test void leaseCoverageRunsAfterAuthorizationAndRevocationDuringCoverageCannotDispatch() {
  var open=service.open(1,grant(2,0,"NORMAL",true));
  var prepared=service.prepare(1,new CompanionObservationService.PrepareInput(open.sessionId(),new ObservationContracts.PageInput("COMPANION",List.of(),List.of())));
  doAnswer(call->{service.close(1,open.sessionId());return null;}).when(quota).coverDispatch(eq(1L),eq(open.sessionId()),any());
  var result=service.observe(1,new CompanionObservationService.ObserveInput(open.sessionId(),prepared.snapshotId(),1));
  assertThat(result.modelDispatched()).isFalse();assertThat(result.textRemaining()).isEqualTo(1);verifyNoInteractions(invoker,visual);
 }
 @Test void noClickSnapshotMakesOneDecisionWithIntensityAndNoVisualOrPlatformCall(){
  when(invoker.streamBounded(any(),anyList(),eq(160))).thenReturn(Flux.just("{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"我想到个小话题\"}"));
  var open=service.open(1,grant(500,0,"LOW",true));
  var snapshot=service.prepare(1,new CompanionObservationService.PrepareInput(open.sessionId(),new ObservationContracts.PageInput("COMPANION",List.of(),List.of())));
  var result=service.observe(1,new CompanionObservationService.ObserveInput(open.sessionId(),snapshot.snapshotId(),1));
  assertThat(result.parsed().action()).isEqualTo("SPEAK");assertThat(result.modelDispatched()).isTrue();
  assertThat(result.trace().messages().getFirst().content()).contains("LOW","300");
  verify(invoker,times(1)).streamBounded(any(),anyList(),eq(160));verifyNoInteractions(visual);verify(region,never()).capture(anyLong(),any(),any());assertThat(result.textRemaining()).isEqualTo(499);
 }
 @Test void emptyIsNotValidSilenceAndDoesNotRetry(){
  when(invoker.streamBounded(any(),anyList(),eq(160))).thenReturn(Flux.empty());
  var open=service.open(1,grant(1,0,"NORMAL",false));
  var snapshot=service.prepare(1,new CompanionObservationService.PrepareInput(open.sessionId(),new ObservationContracts.PageInput("COMPANION",List.of(),List.of())));
  var result=service.observe(1,new CompanionObservationService.ObserveInput(open.sessionId(),snapshot.snapshotId(),1));
  assertThat(result.reason()).isEqualTo("EMPTY_OUTPUT");assertThat(result.parsed()).isNull();assertThat(result.textRemaining()).isZero();
  verify(invoker,times(1)).streamBounded(any(),anyList(),eq(160));
 }
 @Test void ownerBindingChatChangeAndProviderChangeFailBeforeDispatch(){
  var open=service.open(1,grant(6,0,"NORMAL",true));
  assertThatThrownBy(()->service.prepare(2,new CompanionObservationService.PrepareInput(open.sessionId(),new ObservationContracts.PageInput("COMPANION",List.of(),List.of())))).isInstanceOf(IllegalArgumentException.class);
  var snapshot=service.prepare(1,new CompanionObservationService.PrepareInput(open.sessionId(),new ObservationContracts.PageInput("COMPANION",List.of(),List.of())));
  when(context.current(eq(1L),any())).thenReturn(false);
  assertThat(service.observe(1,new CompanionObservationService.ObserveInput(open.sessionId(),snapshot.snapshotId(),1)).reason()).isEqualTo("CONTEXT_CHANGED");
  verifyNoInteractions(invoker,visual);
 }
 @Test void closedAndLateRequestsCannotReviveAndNoQuotaRefund(){
  var open=service.open(1,grant(12,0,"NORMAL",true));service.close(1,open.sessionId());
  assertThatThrownBy(()->service.prepare(1,new CompanionObservationService.PrepareInput(open.sessionId(),new ObservationContracts.PageInput("COMPANION",List.of(),List.of())))).isInstanceOf(IllegalArgumentException.class);
  verifyNoInteractions(invoker,visual);verify(quota,never()).release(anyLong(),anyString(),any(),any());
 }
 @Test void displayedReceiverIsRequiredAndChangingItBeforeEnableCannotDispatch(){
  assertThatThrownBy(()->service.open(1,new ObservationContracts.OpenInput(6,0,"NORMAL",true))).hasMessage("RECEIVER_CHANGED");
  var expected=grant(6,0,"NORMAL",true);
  when(language.inspectCurrentByok(1)).thenReturn(Optional.of(ModelConnection.restore(5L,1,ModelProvider.OPENAI,"replacement",URI.create("https://fixture.invalid/v1"),"changed",4L,true,2)));
  assertThatThrownBy(()->service.open(1,expected)).hasMessage("RECEIVER_CHANGED");verifyNoInteractions(invoker,visual);
 }
 @Test void keyEchoWithQuotedTextRemainsAValidParsedDecisionAfterRedaction(){
  when(invoker.streamBounded(any(),anyList(),eq(160))).thenReturn(Flux.just("{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"echo fixture-secret with \\\"quotes\\\"\"}"));
  var open=service.open(1,grant(6,0,"NORMAL",true));
  var snapshot=service.prepare(1,new CompanionObservationService.PrepareInput(open.sessionId(),new ObservationContracts.PageInput("COMPANION",List.of(),List.of())));
  var result=service.observe(1,new CompanionObservationService.ObserveInput(open.sessionId(),snapshot.snapshotId(),1));
  assertThat(result.parsed().action()).isEqualTo("SPEAK");assertThat(result.parsed().text()).contains("quotes").doesNotContain("fixture-secret");
 }
 @Test void visualCaptionCacheKeepsPixelsAndTransportOnlyInDebugAndNeverRenewsTtlOnHit(){
  final Instant[] now={at};Clock mutable=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now[0];}};
  service=new CompanionObservationService(language,vision,invoker,visual,context,quota,manual,usage,new ObjectMapper().findAndRegisterModules(),mutable,true,region);
  var image=ModelConnection.restore(8L,1,ModelProvider.OPENAI,"vision",URI.create("https://vision.fixture.invalid/v1"),"image-model-only",9L,true,1);
  when(vision.inspectCurrentByok(1)).thenReturn(Optional.of(image));when(vision.decide(1)).thenReturn(ModelRouteDecision.byok(image,"image-secret"));
  var ref=new ObservationContracts.PictureRef(101L,"safe",null,null,at);
  when(context.prepare(eq(1L),any(),any())).thenAnswer(call->new ObservationContracts.Snapshot("GALLERY",List.of(ref),List.of(),List.of(),0,now[0]));
  when(visual.caption(any(),any())).thenReturn(new ObservationVisionClient.Caption("真实图片左红右蓝 fixture-secret image-secret", "真实图片左红右蓝 fixture-secret \\\\u0069mage-secret",true,200,"stop","{\"model\":\"image-model-only\",\"image_url\":\"data:image/png;base64,fixture\"}",null));
  when(invoker.streamBounded(any(),anyList(),eq(160))).thenReturn(Flux.just("{\"action\":\"SILENT\",\"emote\":null,\"text\":null}"));
  var opened=service.open(1,grant(500,100,"NORMAL",true));
  for(int i=0;i<3;i++){
   now[0]=at.plusSeconds(i==0?0:i==1?90:181);
   var prepared=service.prepare(1,new CompanionObservationService.PrepareInput(opened.sessionId(),new ObservationContracts.PageInput("GALLERY",List.of(101L),List.of(new ObservationContracts.Rect(1,1,100,100)))));
   var result=service.observe(1,new CompanionObservationService.ObserveInput(opened.sessionId(),prepared.snapshotId(),i+1,region.capture(1,null,null).pixelDigest()));
   assertThat(result.trace().messages().get(1).content()).contains("真实图片左红右蓝","capturedAt","uncertainty").doesNotContain("data:image","image_url","requestBody","image-model-only");
   assertThat(result.trace().vision().cached()).isEqualTo(i==1);
   try{assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(result)).doesNotContain("fixture-secret","image-secret","u0069mage-secret");}catch(Exception impossible){throw new AssertionError(impossible);}
   if(i!=1)assertThat(result.trace().vision().requestBody()).contains("data:image");
  }
  verify(visual,times(2)).caption(any(),any());verify(invoker,times(3)).streamBounded(any(),anyList(),eq(160));
 }
 @Test void unconfirmedBrowserPixelsFailBeforeAnyQuotaOrModelDispatch(){
  when(vision.inspectCurrentByok(1)).thenReturn(Optional.of(connection));when(vision.decide(1)).thenReturn(ModelRouteDecision.byok(connection,"fixture-secret"));
  when(context.prepare(eq(1L),any(),any())).thenReturn(new ObservationContracts.Snapshot("GALLERY",List.of(new ObservationContracts.PictureRef(101L,"safe",null,null,at)),List.of(),List.of(),0,at));
  var open=service.open(1,grant(2,2,"LOW",true));var prepared=service.prepare(1,new CompanionObservationService.PrepareInput(open.sessionId(),new ObservationContracts.PageInput("GALLERY",List.of(101L),List.of(new ObservationContracts.Rect(1,1,100,100)))));
  var rejected=service.observe(1,new CompanionObservationService.ObserveInput(open.sessionId(),prepared.snapshotId(),1,"wrong-pixel-hash"));
  assertThat(rejected.reason()).isEqualTo("CAPTURE_PIXELS_UNCONFIRMED");assertThat(rejected.textRemaining()).isEqualTo(2);assertThat(rejected.visionRemaining()).isEqualTo(2);verifyNoInteractions(invoker,visual);verify(quota,never()).claim(anyLong(),anyString(),any());verify(quota,never()).reserve(anyLong(),anyString(),anyBoolean(),any());
 }
 @Test void unsupportedRealImageSkipsVisualWithoutChargeWhileTextObservationContinues(){
  when(vision.inspectCurrentByok(1)).thenReturn(Optional.of(connection));when(vision.decide(1)).thenReturn(ModelRouteDecision.byok(connection,"fixture-secret"));
  when(context.prepare(eq(1L),any(),any())).thenReturn(new ObservationContracts.Snapshot("GALLERY",List.of(new ObservationContracts.PictureRef(101L,"safe",null,null,at)),List.of(),List.of(),0,at));
  when(region.capture(anyLong(),any(),any())).thenThrow(new IllegalArgumentException("CAPTURE_FORMAT_UNSUPPORTED"));when(invoker.streamBounded(any(),anyList(),eq(160))).thenReturn(Flux.just("{\"action\":\"SILENT\",\"emote\":null,\"text\":null}"));
  var open=service.open(1,grant(2,2,"LOW",true));var prepared=service.prepare(1,new CompanionObservationService.PrepareInput(open.sessionId(),new ObservationContracts.PageInput("GALLERY",List.of(101L),List.of(new ObservationContracts.Rect(1,1,100,100)))));
  assertThat(prepared.preview()).isNull();assertThat(prepared.visualSkip()).isEqualTo("CAPTURE_FORMAT_UNSUPPORTED");var result=service.observe(1,new CompanionObservationService.ObserveInput(open.sessionId(),prepared.snapshotId(),1));
  assertThat(result.modelDispatched()).isTrue();assertThat(result.visionDispatched()).isFalse();assertThat(result.visionRemaining()).isEqualTo(2);assertThat(result.trace().vision().skip()).isEqualTo("CAPTURE_FORMAT_UNSUPPORTED");verifyNoInteractions(visual);verify(quota,never()).reserve(anyLong(),anyString(),eq(true),any());
 }
 @Test void cancelPermissionsOrEitherRouteChangingDuringVisionReservationNeverSendPixels(){
  for(String change:List.of("close","permission","text","vision")){
   setup();var image=ModelConnection.restore(8L,1,ModelProvider.OPENAI,"vision",URI.create("https://vision.fixture.invalid/v1"),"vision-model",9L,true,1);when(vision.inspectCurrentByok(1)).thenReturn(Optional.of(image));when(vision.decide(1)).thenReturn(ModelRouteDecision.byok(image,"image-secret"));
   when(context.prepare(eq(1L),any(),any())).thenReturn(new ObservationContracts.Snapshot("GALLERY",List.of(new ObservationContracts.PictureRef(101L,"safe",null,null,at)),List.of(),List.of(),0,at));
   var open=service.open(1,grant(2,2,"LOW",true));var prepared=service.prepare(1,new CompanionObservationService.PrepareInput(open.sessionId(),new ObservationContracts.PageInput("GALLERY",List.of(101L),List.of(new ObservationContracts.Rect(1,1,100,100)))));
   when(quota.reserve(anyLong(),anyString(),eq(true),any())).thenAnswer(call->{if(change.equals("close"))service.close(1,open.sessionId());else if(change.equals("permission"))when(region.current(anyLong(),any())).thenReturn(false);else if(change.equals("text"))when(language.inspectCurrentByok(1)).thenReturn(Optional.empty());else when(vision.inspectCurrentByok(1)).thenReturn(Optional.empty());return true;});
   var result=service.observe(1,new CompanionObservationService.ObserveInput(open.sessionId(),prepared.snapshotId(),1,region.capture(1,null,null).pixelDigest()));
   assertThat(result.visionDispatched()).isFalse();assertThat(result.modelDispatched()).isFalse();assertThat(result.trace()).isNull();assertThat(result.visionRemaining()).isEqualTo(1);verify(visual,never()).caption(any(),any());verify(invoker,never()).streamBounded(any(),anyList(),anyInt());
   reset(quota,visual,invoker,region);when(region.enabled()).thenReturn(true);
  }
 }
 @Test void closeAndEitherReceiverChangeDuringTextCallDropEveryPresentationButKeepIncurredAttempt(){
  for(String change:List.of("close","text","vision")){
   setup();var image=ModelConnection.restore(8L,1,ModelProvider.OPENAI,"vision",URI.create("https://vision.fixture.invalid/v1"),"vision-model",9L,true,1);
   when(vision.inspectCurrentByok(1)).thenReturn(Optional.of(image));when(vision.decide(1)).thenReturn(ModelRouteDecision.byok(image,"image-secret"));
   var opened=service.open(1,grant(500,100,"NORMAL",true));var page=new ObservationContracts.PageInput("COMPANION",List.of(),List.of());
   var prepared=service.prepare(1,new CompanionObservationService.PrepareInput(opened.sessionId(),page));
   when(invoker.streamBounded(any(),anyList(),eq(160))).thenAnswer(call->{
    if(change.equals("close"))service.close(1,opened.sessionId());
    else if(change.equals("text"))when(language.inspectCurrentByok(1)).thenReturn(Optional.empty());
    else when(vision.inspectCurrentByok(1)).thenReturn(Optional.empty());
    return Flux.just("{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"late\",\"topic\":\"stale-topic\"}");
   });
   var result=service.observe(1,new CompanionObservationService.ObserveInput(opened.sessionId(),prepared.snapshotId(),1));
   assertThat(result.reason()).isEqualTo("REVOKED");assertThat(result.parsed()).isNull();assertThat(result.trace()).isNull();assertThat(result.modelDispatched()).isTrue();assertThat(result.textRemaining()).isEqualTo(499);
  }
 }
 @Test void manualObservationUsesUnchangedShanghaiBucketAndRevokedFocusNeverLeaks(){
  service=new CompanionObservationService(language,vision,invoker,visual,context,quota,manual,usage,new ObjectMapper().findAndRegisterModules(),Clock.fixed(Instant.parse("2026-10-11T17:00:00Z"),ZoneOffset.UTC),true,region);
  when(invoker.streamBounded(any(),anyList(),eq(160))).thenAnswer(call->{when(context.current(eq(1L),any())).thenReturn(false);return Flux.just("{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"stale\",\"focus\":\"stale-focus\"}");});
  var opened=service.open(1,grant(2,0,"LOW",false));var page=new ObservationContracts.PageInput("COMPANION",List.of(),List.of());
  var prepared=service.prepare(1,new CompanionObservationService.PrepareInput(opened.sessionId(),page));
  var result=service.observe(1,new CompanionObservationService.ObserveInput(opened.sessionId(),prepared.snapshotId(),1));
  assertThat(result.reason()).isEqualTo("REVOKED");assertThat(result.parsed()).isNull();assertThat(result.trace()).isNull();assertThat(result.textRemaining()).isEqualTo(1);
  verify(manual).reserve(1,LocalDate.parse("2026-10-12"),100);
  when(context.current(eq(1L),any())).thenReturn(true);when(invoker.streamBounded(any(),anyList(),eq(160))).thenReturn(Flux.just("{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"fresh\"}"));
  prepared=service.prepare(1,new CompanionObservationService.PrepareInput(opened.sessionId(),page));result=service.observe(1,new CompanionObservationService.ObserveInput(opened.sessionId(),prepared.snapshotId(),2));
  assertThat(result.reason()).isNull();assertThat(result.parsed().text()).isEqualTo("fresh");assertThat(result.trace().messages().get(1).content()).doesNotContain("stale-focus");
 }
}

