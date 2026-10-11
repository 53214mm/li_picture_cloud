package com.li.lipicturecloud.application.companion.observation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.airuntime.ModelRouteDecision;
import com.li.lipicturecloud.domain.airuntime.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
class ObservationVisionClientTest {
 HttpServer server;TestClient client;AtomicReference<String> body=new AtomicReference<>();AtomicReference<String> auth=new AtomicReference<>();
 ModelRouteDecision route=ModelRouteDecision.byok(ModelConnection.restore(7L,1,ModelProvider.OPENAI,"fixture",URI.create("https://fixture.invalid/v1"),"fixture",8L,true,0),"vision-fixture-key");
 static class TestClient extends ObservationVisionClient {
  URI endpoint;TestClient(){super(new ObjectMapper(),HttpClient.newHttpClient(),Duration.ofSeconds(1));}
  @Override protected URI endpoint(ModelRouteDecision route){return endpoint;}
 }
 @BeforeEach void setup() throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.start();client=new TestClient();client.endpoint=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions");}
 @AfterEach void close(){server.stop(0);}
 void respond(String data){server.createContext("/chat/completions",exchange->{body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));auth.set(exchange.getRequestHeaders().getFirst("Authorization"));byte[] bytes=data.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});}
 RealObservationRegion.Preview preview(){try{return new RealObservationRegion.Preview(RealObservationRegionTest.photo(),"fixture-digest","REAL_OBSERVATION_REGION","Allowed title",java.util.Map.of(),List.of());}catch(Exception failed){throw new AssertionError(failed);}}
 @Test void actualHttpHasOnlySafePngAndBoundedByokPayloadAndRetainsNoReasoning() throws Exception{
  respond("{\"choices\":[{\"message\":{\"content\":\"图片左半红色右半蓝色\",\"reasoning_content\":\"PRIVATE_REASONING\"},\"finish_reason\":\"stop\"}]}");
  var result=client.caption(route,preview());
  var request=new ObjectMapper().readTree(body.get());
  assertThat(request.path("max_tokens").intValue()).isEqualTo(160);assertThat(request.path("stream").booleanValue()).isFalse();
  assertThat(body.get()).contains("真实素材","data:image/png;base64,").doesNotContain("vision-fixture-key","tools","reasoning");
  assertThat(auth.get()).isEqualTo("Bearer vision-fixture-key");assertThat(result.requestBody()).isEqualTo(body.get());
  assertThat(result.rawBody()).doesNotContain("PRIVATE_REASONING");assertThat(result.text()).contains("红色");
 }
 @Test void emptyContentIsRecordedWithoutRetryOrFakingAVisualDescription(){
  respond("{\"choices\":[{\"message\":{\"content\":\"\",\"reasoning_content\":\"PRIVATE_REASONING\"},\"finish_reason\":\"length\"}]}");
  var result=client.caption(route,preview());assertThat(result.text()).isNull();assertThat(result.reason()).isEqualTo("VISION_EMPTY_OUTPUT");assertThat(result.rawBody()).isEmpty();
 }
 @Test void platformAndOversizeFailClosed(){
  assertThatThrownBy(()->client.caption(ModelRouteDecision.platform(),preview())).isInstanceOf(IllegalArgumentException.class);
  respond("x".repeat(17000));assertThatThrownBy(()->client.caption(route,preview())).hasMessage("VISION_FAILED");
 }
}
