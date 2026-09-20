package com.li.lipicturecloud.application.companion;

/**
 * 从受控图片字节生成供应商无关的视觉观察候选。
 * 调用来源（供应商/模型/提示词版本/结果结构版本）随结果一起返回。
 */
public interface VisualObservationProvider {

    VisualObservationResult observe(AuthorizedPictureContent content, long subjectId);
}
