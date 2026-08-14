package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.CredentialVaultRepository;
import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.ModelCapabilities;
import com.li.lipicturecloud.domain.airuntime.ModelCapabilityProfile;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRule;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRuleRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.infrastructure.airuntime.PropertyEndpointAllowlist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisionRouterTest {

    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");

    private TaskRoutingRuleRepository routingRepository;
    private ModelConnectionRepository connectionRepository;
    private CredentialVaultRepository vaultRepository;
    private CredentialCipher cipher;
    private EndpointAllowlist allowlist;
    private ModelCapabilityProfileService profileService;
    private VisionRouter router;

    @BeforeEach
    void setUp() {
        routingRepository = mock(TaskRoutingRuleRepository.class);
        connectionRepository = mock(ModelConnectionRepository.class);
        vaultRepository = mock(CredentialVaultRepository.class);
        cipher = mock(CredentialCipher.class);
        allowlist = new PropertyEndpointAllowlist(List.of("deepseek.com"));
        profileService = mock(ModelCapabilityProfileService.class);
        router = new VisionRouter(routingRepository, connectionRepository, vaultRepository, cipher,
                allowlist, profileService);
    }

    private ModelConnection connection() {
        return ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK, "主力",
                URI.create("https://api.deepseek.com/v1"), "deepseek-chat", 5L, true, 1L);
    }

    private CredentialVault credential() {
        return CredentialVault.restore(5L, 7L, ModelProvider.DEEPSEEK, "1234",
                "cipher", CredentialVault.ALGORITHM_AES_GCM_V1, 0L);
    }

    private ModelCapabilityProfile visionProfile() {
        return ModelCapabilityProfile.snapshot(9L, 7L, ModelProvider.DEEPSEEK, "deepseek-chat",
                ModelCapabilities.of(true, true, false, false, false, false, false, 64_000,
                        ModelCapabilities.SYNC, ModelCapabilities.COST_CHEAP), NOW).withId(2L);
    }

    private void stubByokRule() {
        when(routingRepository.findBySubjectAndTask(7L, ModelTask.VISION_UNDERSTANDING))
                .thenReturn(Optional.of(TaskRoutingRule.create(7L, ModelTask.VISION_UNDERSTANDING, 9L)
                        .withId(1L)));
    }

    @Test
    void missingRuleOrExplicitPlatformYieldsPlatformRoute() {
        when(routingRepository.findBySubjectAndTask(7L, ModelTask.VISION_UNDERSTANDING))
                .thenReturn(Optional.empty());
        assertThat(router.decide(7L)).isEqualTo(ModelRouteDecision.platform());

        when(routingRepository.findBySubjectAndTask(7L, ModelTask.VISION_UNDERSTANDING))
                .thenReturn(Optional.of(TaskRoutingRule.create(7L, ModelTask.VISION_UNDERSTANDING, null)
                        .withId(1L)));
        assertThat(router.decide(7L)).isEqualTo(ModelRouteDecision.platform());
    }

    @Test
    void capableByokConnectionYieldsByokRouteWithDecryptedKey() {
        stubByokRule();
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(connection()));
        when(vaultRepository.findById(5L)).thenReturn(Optional.of(credential()));
        when(cipher.decrypt(credential())).thenReturn("sk-secret");
        when(profileService.findLatest(9L)).thenReturn(Optional.of(visionProfile()));

        ModelRouteDecision decision = router.decide(7L);

        assertThat(decision.costSource()).isEqualTo(CostSource.BYOK);
        assertThat(decision.connection().id()).isEqualTo(9L);
        assertThat(decision.apiKey()).isEqualTo("sk-secret");
    }

    @Test
    void brokenByokRulesFailLoudlyInsteadOfFallingBackToPlatform() {
        stubByokRule();

        when(connectionRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> router.decide(7L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("路由规则");

        ModelConnection disabled = ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK, "主力",
                URI.create("https://api.deepseek.com/v1"), "deepseek-chat", 5L, false, 1L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(disabled));
        assertThatThrownBy(() -> router.decide(7L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已停用");

        ModelConnection withoutCredential = ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "主力", URI.create("https://api.deepseek.com/v1"), "deepseek-chat", null, true, 1L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(withoutCredential));
        assertThatThrownBy(() -> router.decide(7L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("未绑定凭据");

        ModelConnection blocked = ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK, "主力",
                URI.create("https://evil.example.com/v1"), "deepseek-chat", 5L, true, 1L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(blocked));
        assertThatThrownBy(() -> router.decide(7L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不在允许清单内");
    }

    @Test
    void capabilityGateRejectsMissingOrNonVisionProfiles() {
        stubByokRule();
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(connection()));
        when(vaultRepository.findById(5L)).thenReturn(Optional.of(credential()));

        when(profileService.findLatest(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> router.decide(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未生成能力画像");

        ModelCapabilityProfile textOnly = ModelCapabilityProfile.snapshot(9L, 7L,
                ModelProvider.DEEPSEEK, "deepseek-chat",
                ModelCapabilities.of(true, false, true, true, false, false, false, 64_000,
                        ModelCapabilities.SYNC, ModelCapabilities.COST_CHEAP), NOW).withId(2L);
        when(profileService.findLatest(9L)).thenReturn(Optional.of(textOnly));
        assertThatThrownBy(() -> router.decide(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持视觉理解");
    }

    @Test
    void rejectsInvalidSubjectIds() {
        assertThatThrownBy(() -> router.decide(0L)).isInstanceOf(IllegalArgumentException.class);
    }
}
