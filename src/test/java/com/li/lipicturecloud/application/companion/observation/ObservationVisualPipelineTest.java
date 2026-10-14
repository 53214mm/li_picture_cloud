package com.li.lipicturecloud.application.companion.observation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.*;
import com.li.lipicturecloud.application.companion.presence.PresenceQuotaGuard;
import com.li.lipicturecloud.domain.airuntime.*;
import com.li.lipicturecloud.config.CosClientConfig;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.service.PictureService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.net.*;
import java.net.http.HttpClient;
import java.time.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real image decoder/render/permission boundary/service/HTTP adapter; only storage and provider are synthetic. */
class ObservationVisualPipelineTest {
 @Test void blankLegacyOriginalPngReachesOneBoundVisionRequestAndSafeCaption() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var requestBody=new AtomicReference<String>();var requests=new java.util.concurrent.atomic.AtomicInteger();var responseStatus=new java.util.concurrent.atomic.AtomicInteger(200);
  server.createContext("/chat/completions",exchange->{requests.incrementAndGet();requestBody.set(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));int status=responseStatus.get();byte[] bytes=(status==200?"{\"choices\":[{\"message\":{\"content\":\"红蓝图 fixture-vision-key\",\"reasoning_content\":\"PRIVATE_REASONING\"},\"finish_reason\":\"stop\"}]}":"PRIVATE_PROVIDER_ERROR fixture-vision-key").getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
  var json=new ObjectMapper().findAndRegisterModules();var pictures=mock(PictureService.class);var auth=mock(SpaceAuthorizationAccessService.class);var source=mock(CosObservationImageSource.class);var config=new CosClientConfig();config.setHost("https://images.fixture.invalid");var picture=RealObservationRegionTest.picture();picture.setOriginalUrl("");picture.setUrl("https://images.fixture.invalid/p/101.png");when(pictures.getById(101L)).thenReturn(picture);when(source.read("p/101.png")).thenReturn(RealObservationRegionTest.photo());
  try(var region=new RealObservationRegion(pictures,auth,source,config,true)){
   var visual=new ObservationVisionClient(json,HttpClient.newHttpClient(),Duration.ofSeconds(1)){@Override protected URI endpoint(ModelRouteDecision route){return URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions");}};
   var language=mock(LanguageRouter.class);var vision=mock(VisionRouter.class);var invoker=mock(LanguageModelInvoker.class);var context=mock(ObservationContext.class);var quota=mock(ObservationQuota.class);
   var connection=ModelConnection.restore(3L,1,ModelProvider.OPENAI,"synthetic",URI.create("https://fixture.invalid/v1"),"fixture",4L,true,1);
   when(language.inspectCurrentByok(1)).thenReturn(Optional.of(connection));when(vision.inspectCurrentByok(1)).thenReturn(Optional.of(connection));when(language.decide(1)).thenReturn(ModelRouteDecision.byok(connection,"fixture-text-key"));when(vision.decide(1)).thenReturn(ModelRouteDecision.byok(connection,"fixture-vision-key"));
   when(context.prepare(eq(1L),any(),any())).thenReturn(RealObservationRegionTest.snapshot());when(context.current(eq(1L),any())).thenReturn(true);when(quota.status(anyLong(),any())).thenReturn(new ObservationQuota.Counts(1,1,null));when(quota.claim(anyLong(),anyString(),any())).thenReturn(true);when(quota.reserve(anyLong(),anyString(),anyBoolean(),any())).thenReturn(true);when(invoker.streamBounded(any(),anyList(),eq(160))).thenReturn(Flux.just("{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"confirmed visual\"}"));
   var service=new CompanionObservationService(language,vision,invoker,visual,context,quota,mock(PresenceQuotaGuard.class),mock(ModelUsageService.class),json,Clock.fixed(RealObservationRegionTest.AT,ZoneOffset.UTC),true,region);var status=service.status(1);
   var open=service.open(1,new ObservationContracts.OpenInput(6,2,"NORMAL",true,status.textReceiver().token(),status.visionReceiver().token()));
   var prepared=service.prepare(1,new CompanionObservationService.PrepareInput(open.sessionId(),new ObservationContracts.PageInput("GALLERY",List.of(101L),List.of(new ObservationContracts.Rect(0,0,100,100)))));
   byte[] regionBytes=Base64.getDecoder().decode(prepared.preview().substring("data:image/png;base64,".length()));var confirmation=new RealObservationRegion.Preview(regionBytes,prepared.digest(),"REAL_OBSERVATION_REGION","",Map.of(),List.of()).pixelDigest();
   var result=service.observe(1,new CompanionObservationService.ObserveInput(open.sessionId(),prepared.snapshotId(),1,confirmation));
   assertThat(requests.get()).isEqualTo(1);assertThat(result.visionDispatched()).isTrue();assertThat(result.modelDispatched()).isTrue();assertThat(result.textRemaining()).isEqualTo(5);assertThat(result.visionRemaining()).isEqualTo(1);assertThat(result.parsed().text()).isEqualTo("confirmed visual");
   var sent=json.readTree(requestBody.get());String pixels=sent.path("messages").path(0).path("content").path(1).path("image_url").path("url").textValue();var image=ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(pixels.substring("data:image/png;base64,".length()))));assertThat(image.getWidth()).isEqualTo(640);assertThat(image.getHeight()).isEqualTo(360);assertThat(image.getRGB(100,100)&0xffffff).isEqualTo(0xff0000);assertThat(image.getRGB(500,100)&0xffffff).isEqualTo(0x0000ff);
   assertThat(result.trace().vision().httpStatus()).isEqualTo(200);assertThat(result.trace().vision().caption()).contains("红蓝图").doesNotContain("fixture-vision-key");assertThat(json.writeValueAsString(result)).doesNotContain("PRIVATE_REASONING","fixture-text-key","fixture-vision-key");verify(auth,atLeast(2)).checkForUser("picture:view",101L,1L);
   // A separately explicit new grant tests rejection; production does not retry this paid request.
   responseStatus.set(401);var rejectedOpen=service.open(1,new ObservationContracts.OpenInput(6,2,"NORMAL",true,status.textReceiver().token(),status.visionReceiver().token()));
   var rejectedPrepare=service.prepare(1,new CompanionObservationService.PrepareInput(rejectedOpen.sessionId(),new ObservationContracts.PageInput("GALLERY",List.of(101L),List.of(new ObservationContracts.Rect(0,0,100,100)))));
   var rejected=service.observe(1,new CompanionObservationService.ObserveInput(rejectedOpen.sessionId(),rejectedPrepare.snapshotId(),1,confirmation));
   assertThat(requests.get()).isEqualTo(2);assertThat(rejected.reason()).isEqualTo("VISION_FAILED");assertThat(rejected.visionDispatched()).isTrue();assertThat(rejected.modelDispatched()).isFalse();assertThat(rejected.textRemaining()).isEqualTo(6);assertThat(rejected.visionRemaining()).isEqualTo(1);assertThat(rejected.trace().vision().httpStatus()).isEqualTo(401);assertThat(rejected.trace().vision().skip()).isEqualTo("VISION_HTTP_ERROR");assertThat(rejected.trace().vision().rawBody()).isNull();assertThat(json.writeValueAsString(rejected)).doesNotContain("PRIVATE_PROVIDER_ERROR","fixture-vision-key","fixture-text-key");verify(invoker,times(1)).streamBounded(any(),anyList(),eq(160));
  }finally{server.stop(0);}
 }
}
