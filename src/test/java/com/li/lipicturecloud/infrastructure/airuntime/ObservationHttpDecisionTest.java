package com.li.lipicturecloud.infrastructure.airuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.*;
import com.li.lipicturecloud.application.companion.observation.*;
import com.li.lipicturecloud.application.companion.presence.PresenceQuotaGuard;
import com.li.lipicturecloud.domain.airuntime.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Full observation service through the actual SSE transport, exclusively loopback fixture. */
class ObservationHttpDecisionTest {
 HttpServer upstream;String response,requestBody;AtomicInteger calls=new AtomicInteger();
 ObjectMapper json=new ObjectMapper().findAndRegisterModules();CompanionObservationService service;
 static class LocalClient extends OpenAiCompatibleLanguageClient {
  final URI uri;LocalClient(URI uri){super(new ObjectMapper(),HttpClient.newHttpClient(),Duration.ofSeconds(2));this.uri=uri;}
  @Override URI resolveEndpoint(ModelRouteDecision route){return uri;}
 }
 @BeforeEach void setup() throws Exception {
  upstream=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  upstream.createContext("/chat/completions",exchange->{calls.incrementAndGet();requestBody=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);byte[] body=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});upstream.start();
  var language=mock(LanguageRouter.class);var vision=mock(VisionRouter.class);var context=mock(ObservationContext.class);var quota=mock(ObservationQuota.class);
  var at=Instant.parse("2026-10-11T17:00:00Z");var connection=ModelConnection.restore(3L,1,ModelProvider.OPENAI,"fixture",URI.create("https://fixture.invalid/v1"),"text-fixture",4L,true,1);
  when(language.inspectCurrentByok(1)).thenReturn(Optional.of(connection));when(language.decide(1)).thenReturn(ModelRouteDecision.byok(connection,"fixture-secret"));when(vision.inspectCurrentByok(1)).thenReturn(Optional.empty());
  when(context.prepare(eq(1L),any(),any())).thenReturn(new ObservationContracts.Snapshot("COMPANION",List.of(),List.of(new ObservationContracts.ChatLine(11L,"USER","本站授权聊天",at)),List.of(),9,at));when(context.current(eq(1L),any())).thenReturn(true);
  when(quota.claim(anyLong(),anyString(),any())).thenReturn(true);when(quota.reserve(anyLong(),anyString(),anyBoolean(),any())).thenReturn(true);when(quota.status(anyLong(),any())).thenReturn(new ObservationQuota.Counts(calls.get(),0,null));
  service=new CompanionObservationService(language,vision,new LocalClient(URI.create("http://127.0.0.1:"+upstream.getAddress().getPort()+"/chat/completions")),mock(ObservationVisionClient.class),context,quota,mock(PresenceQuotaGuard.class),mock(ModelUsageService.class),json,Clock.fixed(at,ZoneOffset.UTC),true);
 }
 @AfterEach void cleanup(){upstream.stop(0);}
 CompanionObservationService.Result observe(){
  var token=service.status(1).textReceiver().token();var grant=service.open(1,new ObservationContracts.OpenInput(500,0,"LOW",true,token,null));
  var prepared=service.prepare(1,new CompanionObservationService.PrepareInput(grant.sessionId(),new ObservationContracts.PageInput("COMPANION",List.of(),List.of())));
  return service.observe(1,new CompanionObservationService.ObserveInput(grant.sessionId(),prepared.snapshotId(),1));
 }
 String frame(String content,String reasoning,String finish)throws Exception {
  var delta=new LinkedHashMap<String,String>();if(content!=null)delta.put("content",content);if(reasoning!=null)delta.put("reasoning_content",reasoning);
  var choice=new LinkedHashMap<String,Object>();choice.put("delta",delta);choice.put("finish_reason",finish);return "data: "+json.writeValueAsString(Map.of("choices",List.of(choice)))+"\n\n";
 }
 @Test void actualRequestExactlyMatchesVisibleTraceAndOneStrictSpeakDecision() throws Exception {
  response=frame("{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"自然的小话题\"}",null,"stop");var result=observe();var sent=json.readTree(requestBody);
  assertThat(result.parsed().action()).isEqualTo("SPEAK");assertThat(calls).hasValue(1);assertThat(result.textRemaining()).isEqualTo(499);
  assertThat(sent.path("max_tokens").asInt()).isEqualTo(160);assertThat(sent.path("stream").asBoolean()).isTrue();assertThat(sent.has("tools")).isFalse();assertThat(sent.has("thinking")).isFalse();assertThat(sent.has("temperature")).isFalse();
  assertThat(sent.path("messages").get(0).path("content").asText()).isEqualTo(result.trace().messages().get(0).content()).contains("LOW","300");
  assertThat(sent.path("messages").get(1).path("content").asText()).isEqualTo(result.trace().messages().get(1).content()).contains("本站授权聊天");
  assertThat(requestBody).doesNotContain("fixture-secret");assertThat(result.trace().stream().httpStatus()).isEqualTo(200);assertThat(result.trace().stream().finishReason()).isEqualTo("stop");
 }
 @Test void reasoningOnlyLengthFinishIsEmptyAndNeverSilenceOrAutomaticRetry() throws Exception {
  response=frame(null,"fixture-private-reasoning","length");var result=observe();assertThat(result.reason()).isEqualTo("EMPTY_OUTPUT");assertThat(result.parsed()).isNull();assertThat(result.trace().rawBody()).isEmpty();assertThat(result.trace().stream().reasoningPresent()).isTrue();assertThat(result.trace().stream().finishReason()).isEqualTo("length");assertThat(json.writeValueAsString(result)).doesNotContain("fixture-private-reasoning");assertThat(calls).hasValue(1);
 }
 @Test void strictSilentAndPartialBrokenStreamAreDifferentResults()throws Exception {
  response=frame("{\"action\":\"SILENT\",\"emote\":null,\"text\":null}",null,"stop");assertThat(observe().parsed().action()).isEqualTo("SILENT");
  response=frame("{\"action\":",null,null)+"data: fixture-malformed-json\n\n";var partial=observe();assertThat(partial.parsed()).isNull();assertThat(partial.reason()).isEqualTo("MODEL_FAILED");assertThat(partial.trace().rawBody()).isEqualTo("{\"action\":");assertThat(partial.trace().complete()).isFalse();assertThat(calls).hasValue(2);
 }
}
