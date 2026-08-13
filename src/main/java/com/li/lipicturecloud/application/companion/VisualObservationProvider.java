package com.li.lipicturecloud.application.companion;

/**
 * 从受控图片字节生成供应商无关的视觉观察候选。
 */
@FunctionalInterface
public interface VisualObservationProvider {
    VisualObservationCandidate observe(AuthorizedPictureContent content);
}
