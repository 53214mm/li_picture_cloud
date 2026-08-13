package com.li.lipicturecloud.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.li.lipicturecloud.config.CosClientConfig;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.CosManager;
import com.li.lipicturecloud.model.dto.file.UploadPictureResult;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Date;
import java.util.List;

@Slf4j
public abstract class PictureUploadTemplate {  
  
    @Resource
    protected CosManager cosManager;
  
    @Resource  
    protected CosClientConfig cosClientConfig;
  
    /**  
     * 模板方法，定义上传流程  
     */  
    public final UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 1. 校验图片  
        validPicture(inputSource);  
  
        // 2. 图片上传地址  
        String uuid = RandomUtil.randomString(16);
        String originFilename = getOriginFilename(inputSource);  
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originFilename));
        String uploadPath = objectKey(uploadPathPrefix, uploadFilename);
  
        File file = null;
        try {  
            // 3. 创建临时文件  
            // 临时文件名和 COS 对象键必须分开：对象键包含目录分隔符，不能作为本地临时文件前缀。
            file = File.createTempFile(uploadFilename, null);
            // 处理文件来源（本地或 URL）  
            processFile(inputSource, file);  
  
            // 4. 上传图片到对象存储  
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();

            // 获取图片处理结果（webp 压缩），失败或不存在时回退到原图
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            if (processResults != null) {
                List<CIObject> objectList = processResults.getObjectList();
                if (CollUtil.isNotEmpty(objectList)) {
                    CIObject compressedCiObject = objectList.get(0);
                    CIObject thumbnailCiObject = compressedCiObject;
                    if(objectList.size()>1){
                        thumbnailCiObject = objectList.get(1);
                    }
                    return buildResult(originFilename, compressedCiObject, thumbnailCiObject, uploadPath);
                }
            }

            // 5. 封装返回结果（原图）
            return buildResult(originFilename, file, uploadPath, imageInfo);  
        } catch (Exception e) {  
            log.error("图片上传到对象存储失败", e);  
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {  
            // 6. 清理临时文件  
            deleteTempFile(file);  
        }  
    }  
  
    /**  
     * 校验输入源（本地文件或 URL）  
     */  
    protected abstract void validPicture(Object inputSource);  
  
    /**  
     * 获取输入源的原始文件名  
     */  
    protected abstract String getOriginFilename(Object inputSource);  
  
    /**  
     * 处理输入源并生成本地临时文件  
     */  
    protected abstract void processFile(Object inputSource, File file) throws Exception;

  
    /**  
     * 封装返回结果  
     */  
    private UploadPictureResult buildResult(String originFilename, File file, String uploadPath, ImageInfo imageInfo) {  
        UploadPictureResult uploadPictureResult = new UploadPictureResult();  
        int picWidth = imageInfo.getWidth();  
        int picHeight = imageInfo.getHeight();  
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));  
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        String fmt = imageInfo.getFormat();
        uploadPictureResult.setPicFormat(StrUtil.isNotBlank(fmt) ? fmt : "jpg");
        uploadPictureResult.setPicSize(FileUtil.size(file));
        String originalUrl = cosClientConfig.getHost() + "/" + uploadPath;
        uploadPictureResult.setUrl(originalUrl);
        uploadPictureResult.setOriginalUrl(originalUrl);
        return uploadPictureResult;  
    }

    private UploadPictureResult buildResult(String originFilename, CIObject compressedCiObject,
                                              CIObject thumbnailCiObject, String uploadPath) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        String format = compressedCiObject.getFormat();
        uploadPictureResult.setPicFormat(StrUtil.isNotBlank(format) ? format : "webp");
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());
        // 设置图片为压缩后的地址
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressedCiObject.getKey());
        // 设置缩略图地址
        uploadPictureResult.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnailCiObject.getKey());
        uploadPictureResult.setOriginalUrl(cosClientConfig.getHost() + "/" + uploadPath);
        return uploadPictureResult;
    }


    /**  
     * 删除临时文件  
     */  
    public void deleteTempFile(File file) {  
        if (file == null) {  
            return;  
        }  
        boolean deleteResult = file.delete();  
        if (!deleteResult) {  
            log.error("file delete error, filepath = {}", file.getAbsolutePath());  
        }  
    }  

    static String objectKey(String uploadPathPrefix, String uploadFilename) {
        return String.format("%s/%s", uploadPathPrefix, uploadFilename);
    }
}
