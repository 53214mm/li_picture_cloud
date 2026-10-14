package com.li.lipicturecloud.application.companion.observation;
import com.li.lipicturecloud.config.CosClientConfig;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.*;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CosObservationImageSourceTest {
 @Test void boundedReadPreservesItsOwnLimitReasonAndScrubsUnknownSdkErrors() throws Exception {
  var config=new CosClientConfig();config.setBucket("synthetic");var source=new CosObservationImageSource(config);var cos=mock(COSClient.class);
  var field=CosObservationImageSource.class.getDeclaredField("client");field.setAccessible(true);field.set(source,cos);
  try{
   var object=new COSObject();object.setObjectContent(new ByteArrayInputStream(new byte[CosObservationImageSource.MAX_BYTES+1]));when(cos.getObject(any(GetObjectRequest.class))).thenReturn(object);
   assertThatThrownBy(()->source.read("fixture.png")).hasMessage("CAPTURE_IMAGE_TOO_LARGE");
   when(cos.getObject(any(GetObjectRequest.class))).thenThrow(new IllegalArgumentException("PRIVATE_SDK_KEY"));assertThatThrownBy(()->source.read("fixture.png")).hasMessage("CAPTURE_IMAGE_UNAVAILABLE");
  }finally{source.close();}
 }
}
