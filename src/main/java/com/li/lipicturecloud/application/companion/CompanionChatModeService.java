package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.airuntime.ModelQuickSetupService;
import com.li.lipicturecloud.application.airuntime.view.QuickConnectionView;
import com.li.lipicturecloud.application.companion.view.CompanionChatModeView;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.companion.CompanionChatMode;
import com.li.lipicturecloud.domain.companion.CompanionChatPreferenceRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 伙伴聊天模式：用户在演示回复与真实模型之间切换。
 *
 * <p>真实模型优先使用用户自己连接的模型；没有时只有服务端允许平台模型（chat-policy=MODEL）
 * 才会使用平台模型并消耗试用额度。服务端仅演示时，没有自己模型的用户不能切到真实模型，
 * 平台额度不会被动用。用户从未选择过时，有可用模型就默认真实模型。</p>
 */
@Service
public class CompanionChatModeService {

    private final CompanionChatPreferenceRepository preferenceRepository;
    private final ModelQuickSetupService quickSetupService;
    private final CompanionFeatureProperties properties;

    public CompanionChatModeService(CompanionChatPreferenceRepository preferenceRepository,
                                    ModelQuickSetupService quickSetupService,
                                    CompanionFeatureProperties properties) {
        this.preferenceRepository = preferenceRepository;
        this.quickSetupService = quickSetupService;
        this.properties = properties;
    }

    /** 本轮对话实际使用的模式。 */
    public CompanionChatMode effectiveMode(long subjectId) {
        return CompanionChatMode.valueOf(view(subjectId).mode());
    }

    public CompanionChatModeView view(long subjectId) {
        checkIdentity(subjectId);
        QuickConnectionView own = quickSetupService.current(subjectId);
        boolean hasOwnModel = own.status() != QuickConnectionView.Status.PLATFORM;
        boolean platformAllowed = properties.getChatPolicy()
                == CompanionFeatureProperties.CompanionChatPolicy.MODEL;
        boolean modelAvailable = hasOwnModel || platformAllowed;
        CompanionChatMode preferred = preferenceRepository.findMode(subjectId)
                .orElse(modelAvailable ? CompanionChatMode.MODEL : CompanionChatMode.DEMO);
        CompanionChatMode effective = preferred == CompanionChatMode.MODEL && modelAvailable
                ? CompanionChatMode.MODEL : CompanionChatMode.DEMO;
        String source = hasOwnModel ? "OWN" : platformAllowed ? "PLATFORM" : "NONE";
        return new CompanionChatModeView(effective.name(), modelAvailable, source,
                own.status() == QuickConnectionView.Status.BROKEN,
                hasOwnModel ? own.providerLabel() : null, hasOwnModel ? own.modelCode() : null);
    }

    /** 用户切换模式。切到真实模型前必须有可用的模型。 */
    public CompanionChatModeView choose(long subjectId, CompanionChatMode mode) {
        checkIdentity(subjectId);
        Objects.requireNonNull(mode, "mode");
        if (mode == CompanionChatMode.MODEL && !view(subjectId).modelAvailable()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "还没有可用的模型，请先在模型连接里连接一个模型");
        }
        preferenceRepository.saveMode(subjectId, mode);
        return view(subjectId);
    }

    private static void checkIdentity(long subjectId) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
    }
}
