package com.li.lipicturecloud.application.companion.observation;

import com.li.lipicturecloud.config.CosClientConfig;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.region.Region;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

/** Dedicated bounded read-only COS client. Never accepts a host or credential from the browser. */
@Component
public class CosObservationImageSource {
    public static final int MAX_BYTES=2*1024*1024;
    private final CosClientConfig config;
    private COSClient client;
    public CosObservationImageSource(CosClientConfig config){this.config=config;}
    private synchronized COSClient client(){
        if(client==null){
            var settings=new ClientConfig(new Region(config.getRegion()));
            settings.setHttpProtocol(HttpProtocol.https);settings.setConnectionTimeout(1500);
            settings.setConnectionRequestTimeout(1500);settings.setSocketTimeout(1500);
            settings.setRequestTimeOutEnable(true);settings.setRequestTimeout(3000);
            settings.setMaxErrorRetry(0);settings.setMaxConnectionsCount(2);
            client=new COSClient(new BasicCOSCredentials(config.getSecretId(),config.getSecretKey()),settings);
        }
        return client;
    }
    public byte[] read(String key){
        try{
            var request=new GetObjectRequest(config.getBucket(),key);request.setRange(0,MAX_BYTES);
            var object=client().getObject(request);
            try(var stream=object.getObjectContent()){
                var bytes=stream.readNBytes(MAX_BYTES+1);
                if(bytes.length==0)throw new IllegalArgumentException("CAPTURE_IMAGE_UNAVAILABLE");
                if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("CAPTURE_IMAGE_TOO_LARGE");
                return bytes;
            }
        }catch(Exception failed){throw new IllegalArgumentException("CAPTURE_IMAGE_TOO_LARGE".equals(failed.getMessage())?"CAPTURE_IMAGE_TOO_LARGE":"CAPTURE_IMAGE_UNAVAILABLE");}
    }
    @PreDestroy public synchronized void close(){if(client!=null){client.shutdown();client=null;}}
}
