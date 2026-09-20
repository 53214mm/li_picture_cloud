package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 本地创作能力的统一可用性判断（能力内核 discover 的最小形态）：哪些玩法已经对用户开放、
 * 未开放时给用户看的原因。
 *
 * <p>阶段 4 的 not-open 守门（文字表情草稿、真实多图融合）与阶段 5 的配方官方模板、
 * 编辑器能力选项、发布/试运行/执行守门共用这一处判断，避免出现"某个入口以为能力可用"的
 * 分裂状态：未开放能力一律 fail-closed——不创建创作任务，也不把配方执行标记为已执行。</p>
 */
@Component
public class LocalCapabilityCatalog {

    /** 一个能力的可用性：未开放时 {@code unavailableReason} 是可直接展示给用户的原因。 */
    public record CapabilityAvailability(CreationKind capability, boolean open, String unavailableReason) {
    }

    public List<CapabilityAvailability> all() {
        return List.of(of(CreationKind.STORY_DRAFT), of(CreationKind.EMOJI_DRAFT),
                of(CreationKind.IMAGE_FUSION));
    }

    public CapabilityAvailability of(CreationKind capability) {
        Objects.requireNonNull(capability, "capability");
        return switch (capability) {
            case STORY_DRAFT -> new CapabilityAvailability(capability, true, null);
            case EMOJI_DRAFT -> new CapabilityAvailability(capability, false,
                    EmojiDraftService.NOT_OPEN_YET_MESSAGE);
            case IMAGE_FUSION -> new CapabilityAvailability(capability, false,
                    FusionImageService.NOT_OPEN_YET_MESSAGE);
        };
    }

    public boolean isOpen(CreationKind capability) {
        return of(capability).open();
    }

    /** 未开放能力大声失败：调用方可以把它转成守门记录，但绝不能继续创建任务。 */
    public void requireOpen(CreationKind capability) {
        CapabilityAvailability availability = of(capability);
        if (!availability.open()) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR,
                    "该动作依赖的能力尚未开放：" + availability.unavailableReason());
        }
    }
}
