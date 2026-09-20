package com.li.lipicturecloud.infrastructure.companion;

import com.li.lipicturecloud.application.companion.AuthorizedPictureRef;
import com.li.lipicturecloud.application.companion.PictureNutritionAnalyzer;
import com.li.lipicturecloud.domain.companion.CompanionSkill;
import com.li.lipicturecloud.domain.companion.MoodImpact;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.NutritionPolicy;
import com.li.lipicturecloud.domain.companion.NutritionProvenance;
import com.li.lipicturecloud.domain.companion.PictureNutrition;
import com.li.lipicturecloud.domain.companion.TraitDelta;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 第一版的可演示分析器。
 *
 * <p>它故意只根据图片 ID 选择固定档案，不读取 URL、文件或图像内容。这样本地演示结果稳定、
 * 隐私边界明确；接入视觉模型时应新增另一实现并如实标记 {@code NutritionMode}。</p>
 */
public class DemoPictureNutritionAdapter implements PictureNutritionAnalyzer {

    @Override
    public NutritionPolicy policy() {
        return NutritionPolicy.DEMO_ONLY;
    }

    @Override
    public NutritionMode mode() {
        return NutritionMode.DEMO_DETERMINISTIC;
    }

    @Override
    public boolean contentUnderstood() {
        return false;
    }

    @Override
    public PictureNutrition analyze(AuthorizedPictureRef picture) {
        // floorMod 让任意合法 long ID 都得到确定且非负的档位，不依赖随机数或当前时间。
        // Demo 档固定情绪影响与记忆种子，让浏览器 E2E 可以稳定断言记忆候选流程。
        MoodImpact demoMood = new MoodImpact(
                bd("2.00"), bd("2.00"), bd("2.00"), bd("2.00"), bd("2.00"));
        return switch (Math.floorMod(picture.pictureId(), 3)) {
            case 0 -> new PictureNutrition(42L,
                    new TraitDelta(bd("0.60"), bd("0.40"), bd("0"), bd("0.20"), bd("0.30")),
                    Map.of(CompanionSkill.IMAGE_OBSERVATION, 18L,
                            CompanionSkill.STORY_CREATION, 12L),
                    "演示营养让伙伴练习了观察与叙事。",
                    NutritionProvenance.demo(), demoMood,
                    "伙伴记得一张让它练习了观察与叙事的演示图片，它把这次练习记进了档案。");
            case 1 -> new PictureNutrition(36L,
                    new TraitDelta(bd("0.20"), bd("0.20"), bd("0.70"), bd("0.10"), bd("0.40")),
                    Map.of(CompanionSkill.IMAGE_OBSERVATION, 15L,
                            CompanionSkill.EMOJI_CREATION, 14L),
                    "演示营养让伙伴练习了观察与表情表达。",
                    NutritionProvenance.demo(), demoMood,
                    "伙伴记得一张让它练习了表情表达的演示图片。");
            default -> new PictureNutrition(48L,
                    new TraitDelta(bd("0.50"), bd("0.10"), bd("0.20"), bd("0.40"), bd("0.80")),
                    Map.of(CompanionSkill.IMAGE_OBSERVATION, 16L,
                            CompanionSkill.IMAGE_FUSION, 10L),
                    "演示营养让伙伴练习了观察与组合想象。",
                    NutritionProvenance.demo(), demoMood,
                    "伙伴记得一张让它练习了组合想象的演示图片。");
        };
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
