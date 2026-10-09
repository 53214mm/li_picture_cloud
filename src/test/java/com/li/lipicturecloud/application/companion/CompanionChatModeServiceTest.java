package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.airuntime.ModelQuickSetupService;
import com.li.lipicturecloud.application.airuntime.view.QuickConnectionView;
import com.li.lipicturecloud.application.companion.view.CompanionChatModeView;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.companion.CompanionChatMode;
import com.li.lipicturecloud.domain.companion.CompanionChatPreferenceRepository;
import com.li.lipicturecloud.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionChatModeServiceTest {

    private static final long USER = 7L;
    private static final QuickConnectionView OWN = new QuickConnectionView(QuickConnectionView.Status.CONNECTED,
            ModelProvider.DEEPSEEK, "DeepSeek", "deepseek-chat", 9L);

    private CompanionChatPreferenceRepository preferences;
    private ModelQuickSetupService quickSetup;
    private CompanionFeatureProperties properties;
    private CompanionChatModeService service;

    @BeforeEach
    void setUp() {
        preferences = mock(CompanionChatPreferenceRepository.class);
        quickSetup = mock(ModelQuickSetupService.class);
        properties = new CompanionFeatureProperties();
        when(preferences.findMode(anyLong())).thenReturn(Optional.empty());
        when(quickSetup.current(anyLong())).thenReturn(QuickConnectionView.platform());
        service = new CompanionChatModeService(preferences, quickSetup, properties);
    }

    @Test
    void demoOnlyServerWithoutOwnModelStaysDemoAndCannotSwitch() {
        CompanionChatModeView view = service.view(USER);

        assertThat(view.mode()).isEqualTo("DEMO");
        assertThat(view.modelAvailable()).isFalse();
        assertThat(view.modelSource()).isEqualTo("NONE");
        assertThatThrownBy(() -> service.choose(USER, CompanionChatMode.MODEL))
                .isInstanceOf(BusinessException.class).hasMessageContaining("先在模型连接");
        verify(preferences, never()).saveMode(anyLong(), any());
    }

    @Test
    void ownModelDefaultsToModelEvenWhenServerIsDemoOnly() {
        when(quickSetup.current(USER)).thenReturn(OWN);

        CompanionChatModeView view = service.view(USER);

        assertThat(view.mode()).isEqualTo("MODEL");
        assertThat(view.modelSource()).isEqualTo("OWN");
        assertThat(view.providerLabel()).isEqualTo("DeepSeek");
        assertThat(view.modelCode()).isEqualTo("deepseek-chat");
        assertThat(service.effectiveMode(USER)).isEqualTo(CompanionChatMode.MODEL);
    }

    @Test
    void platformModelIsOfferedOnlyWhenServerAllowsIt() {
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);

        CompanionChatModeView view = service.view(USER);

        assertThat(view.mode()).isEqualTo("MODEL");
        assertThat(view.modelSource()).isEqualTo("PLATFORM");
        assertThat(view.providerLabel()).isNull();
    }

    @Test
    void explicitDemoChoiceWinsAndUnavailableModelChoiceFallsBackToDemo() {
        when(quickSetup.current(USER)).thenReturn(OWN);
        when(preferences.findMode(USER)).thenReturn(Optional.of(CompanionChatMode.DEMO));
        assertThat(service.effectiveMode(USER)).isEqualTo(CompanionChatMode.DEMO);

        when(quickSetup.current(USER)).thenReturn(QuickConnectionView.platform());
        when(preferences.findMode(USER)).thenReturn(Optional.of(CompanionChatMode.MODEL));
        assertThat(service.effectiveMode(USER)).isEqualTo(CompanionChatMode.DEMO);
    }

    @Test
    void brokenOwnModelStaysSelectedSoChatFailsLoudlyInsteadOfSpendingPlatformQuota() {
        properties.setChatPolicy(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        when(quickSetup.current(USER)).thenReturn(new QuickConnectionView(QuickConnectionView.Status.BROKEN,
                null, null, null, 9L));

        CompanionChatModeView view = service.view(USER);

        assertThat(view.mode()).isEqualTo("MODEL");
        assertThat(view.modelSource()).isEqualTo("OWN");
        assertThat(view.modelBroken()).isTrue();
    }

    @Test
    void choosePersistsAndReturnsTheNewView() {
        when(quickSetup.current(USER)).thenReturn(OWN);
        when(preferences.findMode(USER)).thenReturn(Optional.of(CompanionChatMode.DEMO));

        service.choose(USER, CompanionChatMode.DEMO);

        verify(preferences).saveMode(USER, CompanionChatMode.DEMO);
    }
}
