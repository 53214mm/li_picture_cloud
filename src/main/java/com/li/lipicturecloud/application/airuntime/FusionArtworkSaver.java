package com.li.lipicturecloud.application.airuntime;

/**
 * 融合作品回库端口：复用现有图片上传/保存管线（空间写权限、额度、
 * 审核与可见性规则），返回新作品图片 ID。原图永不覆盖。
 */
public interface FusionArtworkSaver {

    long save(FusionArtworkSaveRequest request);
}
