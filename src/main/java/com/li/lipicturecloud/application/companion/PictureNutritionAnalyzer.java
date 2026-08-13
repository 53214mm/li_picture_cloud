package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.PictureNutrition;

public interface PictureNutritionAnalyzer {
    NutritionMode mode();
    boolean contentUnderstood();
    PictureNutrition analyze(AuthorizedPictureRef picture);
}
