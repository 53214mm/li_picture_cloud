package com.li.lipicturecloud.infrastructure.companion;

import com.li.lipicturecloud.application.companion.AuthorizedPictureContent;
import com.li.lipicturecloud.application.companion.AuthorizedPictureContentProvider;
import com.li.lipicturecloud.application.companion.AuthorizedPictureRef;
import com.li.lipicturecloud.application.companion.PictureNutritionAnalyzer;
import com.li.lipicturecloud.application.companion.VisualObservationCandidate;
import com.li.lipicturecloud.application.companion.VisualObservationProvider;
import com.li.lipicturecloud.application.companion.VisualObservationResult;
import com.li.lipicturecloud.application.companion.VisionQuotaGuard;
import com.li.lipicturecloud.application.companion.VisionSafeFailure;
import com.li.lipicturecloud.domain.companion.CompanionSkill;
import com.li.lipicturecloud.domain.companion.MoodImpact;
import com.li.lipicturecloud.domain.companion.NutritionMode;
import com.li.lipicturecloud.domain.companion.NutritionPolicy;
import com.li.lipicturecloud.domain.companion.NutritionProvenance;
import com.li.lipicturecloud.domain.companion.PictureNutrition;
import com.li.lipicturecloud.domain.companion.TraitDelta;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 将模型的受限观察候选转换成伙伴候选营养。
 *
 * <p>这里没有、也不能直接修改伙伴。所有数值仍会由 {@code CompanionBalance} 在结算时裁剪。
 * 图片先由 {@link AuthorizedPictureContentProvider} 做二次授权与版本校验；额度只在准备把已经
 * 校验的像素发往模型前预占。可恢复错误才会明确降级成元数据营养，错误凭据、权限和额度问题
 * 不会被掩盖。</p>
 */
public final class VisualPictureNutritionAdapter implements PictureNutritionAnalyzer {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Set<String> FALLBACK_CODES = Set.of(
            "VISION_TIMEOUT", "VISION_RATE_LIMITED", "VISION_UNAVAILABLE", "VISION_INVALID_RESPONSE",
            "VISION_IMAGE_TOO_LARGE", "VISION_UNSUPPORTED_IMAGE_FORMAT", "VISION_IMAGE_UNAVAILABLE");

    private final VisionQuotaGuard quota;
    private final AuthorizedPictureContentProvider contents;
    private final VisualObservationProvider visual;
    private final MetadataPictureNutritionAdapter metadata;
    private final Clock clock;
    private final long maxBytes;
    private final int dailyLimit;

    public VisualPictureNutritionAdapter(VisionQuotaGuard quota,
                                         AuthorizedPictureContentProvider contents,
                                         VisualObservationProvider visual,
                                         MetadataPictureNutritionAdapter metadata,
                                         Clock clock,
                                         long maxBytes,
                                         int dailyLimit) {
        this.quota = Objects.requireNonNull(quota, "quota");
        this.contents = Objects.requireNonNull(contents, "contents");
        this.visual = Objects.requireNonNull(visual, "visual");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        if (dailyLimit <= 0) {
            throw new IllegalArgumentException("dailyLimit must be positive");
        }
        this.maxBytes = maxBytes;
        this.dailyLimit = dailyLimit;
    }

    @Override
    public NutritionPolicy policy() {
        return NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK;
    }

    @Override
    public NutritionMode mode() {
        return NutritionMode.VISUAL_MODEL;
    }

    @Override
    public boolean contentUnderstood() {
        // 这是配置状态；每次结果是否真正理解内容必须使用 PictureNutrition.provenance()。
        return true;
    }

    @Override
    public PictureNutrition analyze(AuthorizedPictureRef picture) {
        Objects.requireNonNull(picture, "picture");
        try {
            AuthorizedPictureContent content = contents.load(picture, maxBytes);
            if (content.pictureId() != picture.pictureId()) {
                throw new IllegalStateException("视觉内容与授权图片不一致");
            }
            // 加载失败时尚未出站，不扣平台额度；成功加载后立即预占，任何 Provider 结果都不退款。
            quota.reserve(picture.subject().userId(), LocalDate.now(clock.withZone(SHANGHAI)), dailyLimit);
            // 紧贴外发前再检查一次，避免下载后的分享撤销、移动或替换使旧字节越过权限边界。
            contents.verifyStillAuthorized(picture, content);
            return visualNutrition(visual.observe(content, picture.subject().userId()));
        } catch (RuntimeException exception) {
            if (exception instanceof VisionSafeFailure failure && FALLBACK_CODES.contains(failure.safeCode())) {
                return metadataFallback(picture, failure.safeCode());
            }
            throw exception;
        }
    }

    @Override
    public PictureNutrition analyzeFamiliar(AuthorizedPictureRef picture) {
        Objects.requireNonNull(picture, "picture");
        return metadataFallback(picture, "SKIPPED_FAMILIAR");
    }

    private PictureNutrition visualNutrition(VisualObservationResult result) {
        Objects.requireNonNull(result, "visual observation result");
        VisualObservationCandidate candidate = result.candidate();
        long experience = 35L + candidate.sceneComplexity() * 2L + candidate.energy() + candidate.creativity();
        TraitDelta traits = new TraitDelta(
                decimal("0.20").add(decimal("0.10").multiply(BigDecimal.valueOf(candidate.sceneComplexity()))),
                decimal("0.10").multiply(BigDecimal.valueOf(candidate.energy())),
                (candidate.mood() == VisualObservationCandidate.Mood.JOYFUL ? decimal("0.20") : BigDecimal.ZERO)
                        .add(decimal("0.05").multiply(BigDecimal.valueOf(candidate.motionPotential()))),
                (candidate.socialPresence() ? decimal("0.20") : BigDecimal.ZERO)
                        .add(candidate.mood() == VisualObservationCandidate.Mood.MELANCHOLIC
                                || candidate.mood() == VisualObservationCandidate.Mood.TENSE
                                ? decimal("0.05") : BigDecimal.ZERO),
                decimal("0.10").multiply(BigDecimal.valueOf(candidate.creativity())));
        Map<CompanionSkill, Long> skills = new EnumMap<>(CompanionSkill.class);
        skills.put(CompanionSkill.IMAGE_OBSERVATION,
                12L + candidate.sceneComplexity() * 3L + (atLeast(candidate.confidence(), "0.80") ? 6L : 0L));
        skills.put(CompanionSkill.STORY_CREATION,
                (candidate.socialPresence() ? 6L : 0L) + candidate.creativity() * 2L);
        if (candidate.mood() != VisualObservationCandidate.Mood.NEUTRAL) {
            skills.put(CompanionSkill.EMOJI_CREATION, 4L + candidate.energy());
        }
        return new PictureNutrition(experience, traits, Map.copyOf(skills),
                candidate.companionMessage(),
                NutritionProvenance.visual(result.providerCode(), result.modelCode(),
                        result.promptVersion(), result.resultSchemaVersion(), candidate.confidence()),
                moodImpact(candidate), candidate.companionMessage());
    }

    /**
     * 把视觉候选的情绪线索映射成伙伴情绪影响；数值仍只是候选，最终由 {@code CompanionMoodRules} 截断。
     */
    private static MoodImpact moodImpact(VisualObservationCandidate candidate) {
        BigDecimal energy = decimal("2.00").multiply(BigDecimal.valueOf(candidate.energy()));
        BigDecimal joy = switch (candidate.mood()) {
            case JOYFUL -> decimal("8.00");
            case CALM -> decimal("3.00");
            case MELANCHOLIC -> decimal("-5.00");
            default -> BigDecimal.ZERO;
        };
        BigDecimal loneliness = switch (candidate.mood()) {
            case JOYFUL -> decimal("-4.00");
            case MELANCHOLIC -> decimal("7.00");
            default -> BigDecimal.ZERO;
        };
        BigDecimal irritation = switch (candidate.mood()) {
            case CALM -> decimal("-5.00");
            case TENSE -> decimal("7.00");
            default -> BigDecimal.ZERO;
        };
        BigDecimal energyFromTense = candidate.mood() == VisualObservationCandidate.Mood.TENSE
                ? decimal("-4.00") : BigDecimal.ZERO;
        return new MoodImpact(energy.add(energyFromTense), joy, loneliness,
                decimal("2.00").multiply(BigDecimal.valueOf(candidate.creativity())), irritation);
    }

    private PictureNutrition metadataFallback(AuthorizedPictureRef picture, String safeCode) {
        PictureNutrition metadataNutrition = metadata.analyze(picture);
        return new PictureNutrition(metadataNutrition.requestedLifeExperience(), metadataNutrition.requestedTraitDelta(),
                metadataNutrition.requestedSkillExperience(),
                "视觉服务暂不可用，本次使用图片元数据营养。",
                NutritionProvenance.metadataFallback(safeCode),
                metadataNutrition.requestedMoodImpact(), null);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static boolean atLeast(BigDecimal value, String boundary) {
        return value.compareTo(decimal(boundary)) >= 0;
    }
}
