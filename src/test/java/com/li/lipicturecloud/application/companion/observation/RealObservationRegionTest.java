package com.li.lipicturecloud.application.companion.observation;

import com.li.lipicturecloud.config.CosClientConfig;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.service.PictureService;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealObservationRegionTest {
 static final Instant AT=Instant.parse("2026-10-11T00:00:00Z");
 static byte[] photo() throws Exception {var img=new BufferedImage(100,50,BufferedImage.TYPE_INT_RGB);var g=img.createGraphics();g.setColor(Color.RED);g.fillRect(0,0,50,50);g.setColor(Color.BLUE);g.fillRect(50,0,50,50);g.setColor(Color.YELLOW);g.fillRect(0,0,100,1);g.setColor(Color.BLACK);g.fillOval(40,20,20,20);g.fillPolygon(new int[]{40,43,48},new int[]{24,15,21},3);g.fillPolygon(new int[]{53,57,60},new int[]{21,15,24},3);g.setColor(Color.WHITE);g.fillOval(45,25,3,3);g.fillOval(52,25,3,3);g.dispose();var out=new ByteArrayOutputStream();ImageIO.write(img,"png",out);return out.toByteArray();}
 static ObservationContracts.Snapshot snapshot(){return new ObservationContracts.Snapshot("GALLERY",List.of(new ObservationContracts.PictureRef(101L,"Allowed title SECRET_MODEL_KEY","nature","blue",AT)),List.of(),List.of(),0,AT);}
 static Picture picture(){var p=new Picture();p.setId(101L);p.setName("Allowed title SECRET_MODEL_KEY");p.setCategory("nature");p.setTags("blue");p.setUpdateTime(java.util.Date.from(AT));p.setUrl("https://images.fixture.invalid/p/101.webp");p.setOriginalUrl("https://images.fixture.invalid/p/101.png");return p;}
 @Test void realPixelsAndOnlyAllowedRedactedTextAreRenderedBeforeAnythingIsSent() throws Exception {
  var pictures=mock(PictureService.class);var auth=mock(SpaceAuthorizationAccessService.class);var source=mock(CosObservationImageSource.class);var config=new CosClientConfig();config.setHost("https://images.fixture.invalid");when(pictures.getById(101L)).thenReturn(picture());when(source.read("p/101.png")).thenReturn(photo());
  try(var region=new RealObservationRegion(pictures,auth,source,config,true)){
   var preview=region.capture(1,snapshot(),new ObservationRedaction("SECRET_MODEL_KEY"));var image=ImageIO.read(new ByteArrayInputStream(preview.bytes()));
   assertThat(preview.source()).isEqualTo("REAL_OBSERVATION_REGION");assertThat(image.getRGB(100,100)&0xffffff).isEqualTo(0xff0000);assertThat(image.getRGB(500,100)&0xffffff).isEqualTo(0x0000ff);
   assertThat(preview.allowedText()).contains("Allowed title [REDACTED_CREDENTIAL]","nature","blue").doesNotContain("SECRET_MODEL_KEY");
   var order=inOrder(auth,pictures,source);order.verify(auth).checkForUser("picture:view",101L,1L);order.verify(pictures).getById(101L);order.verify(source).read("p/101.png");
   assertThat(region.current(1,preview)).isTrue();var changed=picture();changed.setOriginalUrl("https://images.fixture.invalid/p/replaced.png");when(pictures.getById(101L)).thenReturn(changed);assertThat(region.current(1,preview)).isFalse();
   java.nio.file.Files.write(java.nio.file.Path.of("../capture-test-photo-fixture.png"),photo());var path=java.nio.file.Path.of("../capture-real-region-fixture.png");java.nio.file.Files.write(path,preview.bytes());
   java.nio.file.Files.writeString(java.nio.file.Path.of("../capture-real-region-fixture.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("pixelDigest",preview.pixelDigest(),"allowedText",preview.allowedText(),"source",preview.source())));
  }
 }
 @Test void deniedOrUnsupportedOrOffDoesNotReadOrProducePlaceholder() throws Exception {
  var pictures=mock(PictureService.class);var auth=mock(SpaceAuthorizationAccessService.class);var source=mock(CosObservationImageSource.class);var config=new CosClientConfig();config.setHost("https://images.fixture.invalid");
  try(var region=new RealObservationRegion(pictures,auth,source,config,false)){assertThatThrownBy(()->region.capture(1,snapshot(),new ObservationRedaction())).hasMessage("REAL_VISUAL_CAPTURE_UNAVAILABLE");verifyNoInteractions(pictures,auth,source);}
  try(var region=new RealObservationRegion(pictures,auth,source,config,true)){
   doThrow(new IllegalArgumentException("DENIED")).when(auth).checkForUser("picture:view",101L,1L);assertThatThrownBy(()->region.capture(1,snapshot(),new ObservationRedaction())).hasMessage("CAPTURE_IMAGE_UNAVAILABLE");verifyNoInteractions(source);
   reset(auth);var p=picture();p.setOriginalUrl("https://other.invalid/form-secret.png");when(pictures.getById(101L)).thenReturn(p);assertThatThrownBy(()->region.capture(1,snapshot(),new ObservationRedaction())).hasMessage("CAPTURE_IMAGE_UNAVAILABLE");verifyNoInteractions(source);
   p.setOriginalUrl("https://images.fixture.invalid/p/101.webp");when(source.read("p/101.webp")).thenReturn("RIFFxxxxWEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII));assertThatThrownBy(()->region.capture(1,snapshot(),new ObservationRedaction())).hasMessage("CAPTURE_FORMAT_UNSUPPORTED");verify(source).read("p/101.webp");
   p.setOriginalUrl("https://images.fixture.invalid/p/101.png");when(source.read("p/101.png")).thenReturn(new byte[]{1,2,3});assertThatThrownBy(()->region.capture(1,snapshot(),new ObservationRedaction())).hasMessage("CAPTURE_FORMAT_UNSUPPORTED");
  }
 }
 @Test void configuredOriginCannotBecomeArbitraryUrlOrTraversal(){
  assertThat(RealObservationRegion.storageKey("https://images.fixture.invalid/p/101.png","https://images.fixture.invalid")).isEqualTo("p/101.png");
  for(var url:List.of("https://evil.invalid/p/a.png","http://images.fixture.invalid/p/a.png","https://images.fixture.invalid/p/../a.png","https://images.fixture.invalid/p/%2e%2e/a.png","https://images.fixture.invalid/p/a.png?secret=1","https://images.fixture.invalid@evil.invalid/p/a.png"))assertThatThrownBy(()->RealObservationRegion.storageKey(url,"https://images.fixture.invalid")).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void authorizedExtensionlessOriginalJpegUsesMagicAndKeepsRasterBounds() throws Exception {
  var pictures=mock(PictureService.class);var auth=mock(SpaceAuthorizationAccessService.class);var source=mock(CosObservationImageSource.class);var config=new CosClientConfig();config.setHost("https://images.fixture.invalid");var p=picture();p.setOriginalUrl("https://images.fixture.invalid/original_without_suffix");when(pictures.getById(101L)).thenReturn(p);var jpeg=new ByteArrayOutputStream();ImageIO.write(ImageIO.read(new ByteArrayInputStream(photo())),"jpeg",jpeg);when(source.read("original_without_suffix")).thenReturn(jpeg.toByteArray());
  try(var region=new RealObservationRegion(pictures,auth,source,config,true)){var result=ImageIO.read(new ByteArrayInputStream(region.capture(1,snapshot(),new ObservationRedaction()).bytes()));assertThat(result.getWidth()).isEqualTo(640);assertThat(result.getHeight()).isEqualTo(360);verify(auth,atLeast(2)).checkForUser("picture:view",101L,1L);}
 }
 @Test void jpegAndDimensionBombAndByteLimitAreHandledBeforeUnboundedDecode() throws Exception {
  assertThatThrownBy(()->RealObservationRegion.decode("<svg xmlns='http://www.w3.org/2000/svg'><script/></svg>".getBytes(java.nio.charset.StandardCharsets.UTF_8))).hasMessage("CAPTURE_FORMAT_UNSUPPORTED");
  assertThatThrownBy(()->RealObservationRegion.decode("RIFFxxxxWEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII))).hasMessage("CAPTURE_FORMAT_UNSUPPORTED");
  var photo=ImageIO.read(new ByteArrayInputStream(photo()));var jpeg=new ByteArrayOutputStream();ImageIO.write(photo,"jpeg",jpeg);assertThat(RealObservationRegion.decode(jpeg.toByteArray()).getWidth()).isEqualTo(100);
  assertThatThrownBy(()->RealObservationRegion.decode(new byte[CosObservationImageSource.MAX_BYTES+1])).hasMessage("CAPTURE_IMAGE_TOO_LARGE");
  byte[] bomb=photo();var data=java.nio.ByteBuffer.wrap(bomb);data.putInt(16,12000);data.putInt(20,12000);var crc=new java.util.zip.CRC32();crc.update(bomb,12,17);data.putInt(29,(int)crc.getValue());assertThatThrownBy(()->RealObservationRegion.decode(bomb)).hasMessage("CAPTURE_IMAGE_TOO_LARGE");
 }
 @Test void timedOutReadIsInterruptedAndNextCaptureCanRunWithoutQueuedWork() throws Exception {
  var pictures=mock(PictureService.class);var auth=mock(SpaceAuthorizationAccessService.class);var source=mock(CosObservationImageSource.class);var config=new CosClientConfig();config.setHost("https://images.fixture.invalid");when(pictures.getById(101L)).thenReturn(picture());var interrupted=new java.util.concurrent.atomic.AtomicBoolean();
  when(source.read(anyString())).thenAnswer(call->{try{Thread.sleep(10000);return photo();}catch(InterruptedException stop){interrupted.set(true);throw new IllegalArgumentException("CAPTURE_IMAGE_UNAVAILABLE");}});
  try(var region=new RealObservationRegion(pictures,auth,source,config,true)){
   long start=System.nanoTime();assertThatThrownBy(()->region.capture(1,snapshot(),new ObservationRedaction())).hasMessage("CAPTURE_IMAGE_UNAVAILABLE");assertThat(java.time.Duration.ofNanos(System.nanoTime()-start).toSeconds()).isLessThan(7);
   for(int i=0;i<100&&!interrupted.get();i++)Thread.sleep(10);assertThat(interrupted).isTrue();when(source.read(anyString())).thenReturn(photo());assertThat(region.capture(1,snapshot(),new ObservationRedaction()).bytes()).isNotEmpty();
  }
 }
}

