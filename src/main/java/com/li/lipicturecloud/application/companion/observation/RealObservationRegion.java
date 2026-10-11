package com.li.lipicturecloud.application.companion.observation;

import com.li.lipicturecloud.config.CosClientConfig;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.service.PictureService;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PreDestroy;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import static com.li.lipicturecloud.application.companion.observation.ObservationContracts.*;

/** Real authorized image pixels and a text whitelist in an explicitly separate observation region. */
@Component
public class RealObservationRegion implements AutoCloseable {
    private final PictureService pictures;private final SpaceAuthorizationAccessService auth;
    private final CosObservationImageSource source;private final CosClientConfig config;private final boolean enabled;
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(0,2,30,TimeUnit.SECONDS,new SynchronousQueue<>(),task->{var t=new Thread(task,"companion-safe-image-read");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    public RealObservationRegion(PictureService pictures,SpaceAuthorizationAccessService auth,CosObservationImageSource source,CosClientConfig config,
        @Value("${app.companion.observation.real-visual-enabled:false}") boolean enabled){this.pictures=pictures;this.auth=auth;this.source=source;this.config=config;this.enabled=enabled;}
    public boolean enabled(){return enabled;}
    public record Preview(byte[] bytes,String digest,String source,String allowedText,Map<Long,String> versions,List<PictureRef> references){
        public Preview{bytes=bytes.clone();versions=Map.copyOf(versions);references=List.copyOf(references);if(!"REAL_OBSERVATION_REGION".equals(source))throw new IllegalArgumentException("REAL_VISUAL_CAPTURE_REQUIRED");}
        @Override public byte[] bytes(){return bytes.clone();}
        public String pixelDigest(){try{var image=ImageIO.read(new ByteArrayInputStream(bytes));var pixels=new byte[image.getWidth()*image.getHeight()*4];for(int y=0,i=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int rgb=image.getRGB(x,y);pixels[i++]=(byte)(rgb>>16);pixels[i++]=(byte)(rgb>>8);pixels[i++]=(byte)rgb;pixels[i++]=(byte)255;}return hash(pixels);}catch(Exception failed){throw invalid("CAPTURE_IMAGE_UNAVAILABLE");}}
        @Override public String toString(){return "RealObservationRegion[private pixels and metadata omitted]";}
    }
    public Preview capture(long owner,Snapshot snapshot,ObservationRedaction redaction){
        if(!enabled)throw invalid("REAL_VISUAL_CAPTURE_UNAVAILABLE");
        if(!Set.of("GALLERY","PICTURE_DETAIL").contains(snapshot.page()) || snapshot.pictures().isEmpty() || snapshot.pictures().size()>6)throw invalid("NO_SAFE_CARD_REGION");
        Future<Preview> future;
        try{future=executor.submit(()->render(owner,snapshot,redaction));}catch(RejectedExecutionException busy){throw invalid("CAPTURE_BUSY");}
        try{return future.get(5,TimeUnit.SECONDS);}
        catch(InterruptedException interrupted){future.cancel(true);Thread.currentThread().interrupt();throw invalid("CAPTURE_IMAGE_UNAVAILABLE");}
        catch(Exception failed){future.cancel(true);var cause=failed.getCause();if(cause instanceof IllegalArgumentException value && Set.of("CAPTURE_FORMAT_UNSUPPORTED","CAPTURE_IMAGE_TOO_LARGE","NO_SAFE_CARD_REGION").contains(value.getMessage()))throw value;throw invalid("CAPTURE_IMAGE_UNAVAILABLE");}
    }
    private Preview render(long owner,Snapshot snapshot,ObservationRedaction redaction) throws Exception {
        var refs=snapshot.pictures();var images=new ArrayList<BufferedImage>();var versions=new LinkedHashMap<Long,String>();var texts=new ArrayList<String>();
        for(var ref:refs){
            if(Thread.currentThread().isInterrupted())throw invalid("CAPTURE_IMAGE_UNAVAILABLE");
            var p=authorized(owner,ref);String key=storageKey(p.getOriginalUrl()==null?p.getUrl():p.getOriginalUrl(),config.getHost());
            images.add(decode(source.read(key)));versions.put(ref.id(),version(p));
            texts.add(text(redaction.safe(ref.name()),80)+" | "+text(redaction.safe(ref.category()),40)+" | "+text(redaction.safe(ref.tags()),100));
        }
        // Any permission/record change during download discards every pixel; no placeholders or partial capture.
        for(var ref:refs)if(!versions.get(ref.id()).equals(version(authorized(owner,ref))))throw invalid("CAPTURE_IMAGE_UNAVAILABLE");
        var result=new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB);var g=result.createGraphics();
        try{
            g.setColor(Color.WHITE);g.fillRect(0,0,640,360);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.BLACK);g.setFont(new Font("Dialog",Font.PLAIN,14));g.drawString("Observation region / real images + allowed labels",12,20);
            int columns=refs.size()==1?1:refs.size()<=4?2:3,rows=(refs.size()+columns-1)/columns,w=640/columns,h=324/rows;
            for(int i=0;i<images.size();i++){
                int x=i%columns*w+8,y=i/columns*h+32,pw=w-16,ph=h-56;var img=images.get(i);
                double scale=Math.min((double)pw/img.getWidth(),(double)ph/img.getHeight());int iw=Math.max(1,(int)(img.getWidth()*scale)),ih=Math.max(1,(int)(img.getHeight()*scale));
                g.drawImage(img,x+(pw-iw)/2,y+(ph-ih)/2,iw,ih,null);g.setColor(Color.BLACK);g.setFont(new Font("Dialog",Font.PLAIN,12));
                var label=texts.get(i);var fm=g.getFontMetrics();int line=0;var part=new StringBuilder();
                for(int offset=0;offset<label.length() && line<3;){int cp=label.codePointAt(offset);String next=new String(Character.toChars(cp));if(fm.stringWidth(part+next)>pw){g.drawString(part.toString(),x,y+ph+15+line++*13);part.setLength(0);}else{part.append(next);offset+=Character.charCount(cp);}}
                if(line<3)g.drawString(part.toString(),x,y+ph+15+line*13);
            }
        }finally{g.dispose();}
        var output=new ByteArrayOutputStream();ImageIO.write(result,"png",output);byte[] bytes=output.toByteArray();if(bytes.length>768*1024)throw invalid("CAPTURE_IMAGE_TOO_LARGE");
        return new Preview(bytes,hash(bytes),"REAL_OBSERVATION_REGION",String.join("\n",texts),versions,refs);
    }
    private Picture authorized(long owner,PictureRef ref){
        auth.checkForUser("picture:view",ref.id(),owner);var p=pictures.getById(ref.id());
        if(p==null || !new PictureRef(ref.id(),p.getName(),p.getCategory(),p.getTags(),p.getUpdateTime()==null?null:p.getUpdateTime().toInstant()).equals(ref))throw invalid("CAPTURE_IMAGE_UNAVAILABLE");return p;
    }
    public boolean current(long owner,Preview preview){
        if(!enabled)return false;
        try{for(var ref:preview.references())if(!preview.versions().get(ref.id()).equals(version(authorized(owner,ref))))return false;return true;}catch(RuntimeException unavailable){return false;}
    }
    static String storageKey(String value,String configuredHost){
        try{
            var url=URI.create(value);var host=URI.create(configuredHost);
            if(!"https".equals(url.getScheme()) || !"https".equals(host.getScheme()) || url.getHost()==null || !url.getHost().equalsIgnoreCase(host.getHost()) || url.getPort()!=host.getPort() || url.getUserInfo()!=null || url.getQuery()!=null || url.getFragment()!=null)throw invalid("CAPTURE_IMAGE_UNAVAILABLE");
            String path=url.getRawPath(),prefix=host.getRawPath()==null?"":host.getRawPath().replaceFirst("/+$","");
            if(!path.startsWith(prefix+"/") || !path.matches("[A-Za-z0-9_./-]+") || Arrays.asList(path.split("/")).contains("..") || Arrays.asList(path.split("/")).contains("."))throw invalid("CAPTURE_IMAGE_UNAVAILABLE");
            String key=path.substring(prefix.length()+1);if(key.isEmpty() || key.contains("//"))throw invalid("CAPTURE_IMAGE_UNAVAILABLE");return key;
        }catch(RuntimeException invalid){throw invalid("CAPTURE_IMAGE_UNAVAILABLE");}
    }
    static BufferedImage decode(byte[] bytes) throws IOException {
        if(bytes.length>CosObservationImageSource.MAX_BYTES)throw invalid("CAPTURE_IMAGE_TOO_LARGE");
        boolean png=bytes.length>=8 && Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10});
        boolean jpeg=bytes.length>=3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255;
        if(!png && !jpeg)throw invalid("CAPTURE_FORMAT_UNSUPPORTED");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw invalid("CAPTURE_FORMAT_UNSUPPORTED");var reader=readers.next();
            try{reader.setInput(input,true,true);String format=reader.getFormatName().toLowerCase(Locale.ROOT);if(!Set.of("png","jpeg").contains(format))throw invalid("CAPTURE_FORMAT_UNSUPPORTED");int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||w>10000||h>10000||(long)w*h>20_000_000)throw invalid("CAPTURE_IMAGE_TOO_LARGE");var param=reader.getDefaultReadParam();int sample=Math.max(1,(Math.max(w,h)+1023)/1024);param.setSourceSubsampling(sample,sample,0,0);return reader.read(0,param);}finally{reader.dispose();}
        }
    }
    private static String text(String value,int max){if(value==null)return "";value=value.replaceAll("[\\p{Cntrl}\\p{Cf}]"," ");return value.substring(0,Math.min(value.length(),max));}
    private static String version(Picture p){return hash((Objects.toString(p.getUrl(),"")+"\0"+Objects.toString(p.getOriginalUrl(),"")+"\0"+Objects.toString(p.getUpdateTime(),"")).getBytes(StandardCharsets.UTF_8));}
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception impossible){throw invalid("CAPTURE_IMAGE_UNAVAILABLE");}}
    private static IllegalArgumentException invalid(String code){return new IllegalArgumentException(code);}
    @Override @PreDestroy public void close(){executor.shutdownNow();}
}
