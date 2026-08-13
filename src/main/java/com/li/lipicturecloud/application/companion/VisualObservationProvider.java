package com.li.lipicturecloud.application.companion;

/**
 * 从受控图片字节生成供应商无关的视觉观察候选。
 */
public interface VisualObservationProvider {

    VisualObservationCandidate observe(AuthorizedPictureContent content);

    /** 实际提供推理服务的供应商标识，会写进当次成长来源而非全局配置。 */
    String providerCode();

    /** 实际调用的模型标识。 */
    String modelCode();

    /** 产生这份候选所使用的提示词语义版本。 */
    String promptVersion();

    /** 产生这份候选所遵循的结构化结果版本。 */
    String resultSchemaVersion();
}
