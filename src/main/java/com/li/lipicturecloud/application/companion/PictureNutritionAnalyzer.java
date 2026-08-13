package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.NutritionPolicy;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.PictureNutrition;

public interface PictureNutritionAnalyzer {

    /**
     * 本次喂养请求允许采用的路径。实际发生的来源必须从 {@link PictureNutrition#provenance()} 读取。
     */
    NutritionPolicy policy();

    /**
     * @deprecated 仅供旧状态页过渡使用；它描述配置能力，不代表某次分析的实际来源。
     */
    @Deprecated(forRemoval = false)
    NutritionMode mode();

    /**
     * @deprecated 仅供旧状态页过渡使用；每次结果是否理解内容必须从 provenance 读取。
     */
    @Deprecated(forRemoval = false)
    boolean contentUnderstood();

    PictureNutrition analyze(AuthorizedPictureRef picture);
}
