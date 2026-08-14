package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.CredentialVaultRepository;
import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRule;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRuleRepository;
import com.li.lipicturecloud.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LanguageRouterTest {

    private TaskRoutingRuleRepository routingRepository;
    private ModelConnectionRepository connectionRepository;
    private CredentialVaultRepository vaultRepository;
    private CredentialCipher cipher;
    private LanguageRouter router;

    @BeforeEach
    void setUp() {
        routingRepository = mock(TaskRoutingRuleRepository.class);
        connectionRepository = mock(ModelConnectionRepository.class);
        vaultRepository = mock(CredentialVaultRepository.class);
        cipher = mock(CredentialCipher.class);
        router = new LanguageRouter(routingRepository, connectionRepository, vaultRepository, cipher);
    }

    private ModelConnection connection() {
        return ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK, "主力",
                URI.create("https://api.deepseek.com/v1"), "deepseek-chat", 5L, true, 1L);
    }

    private CredentialVault credential() {
        return CredentialVault.restore(5L, 7L, ModelProvider.DEEPSEEK, "1234",
                "cipher", CredentialVault.ALGORITHM_AES_GCM_V1, 0L);
    }

    @Test
    void missingRuleOrExplicitPlatformYieldsPlatformRoute() {
        when(routingRepository.findBySubjectAndTask(7L, ModelTask.LANGUAGE_AGENT))
                .thenReturn(Optional.empty());
        assertThat(router.decide(7L)).isEqualTo(LanguageRouteDecision.platform());

        when(routingRepository.findBySubjectAndTask(7L, ModelTask.LANGUAGE_AGENT))
                .thenReturn(Optional.of(TaskRoutingRule.create(7L, ModelTask.LANGUAGE_AGENT, null)
                        .withId(1L)));
        assertThat(router.decide(7L)).isEqualTo(LanguageRouteDecision.platform());
    }

    @Test
    void boundEnabledConnectionYieldsByokRouteWithDecryptedKey() {
        when(routingRepository.findBySubjectAndTask(7L, ModelTask.LANGUAGE_AGENT))
                .thenReturn(Optional.of(TaskRoutingRule.create(7L, ModelTask.LANGUAGE_AGENT, 9L)
                        .withId(1L)));
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(connection()));
        when(vaultRepository.findById(5L)).thenReturn(Optional.of(credential()));
        when(cipher.decrypt(credential())).thenReturn("sk-secret");

        LanguageRouteDecision decision = router.decide(7L);

        assertThat(decision.costSource()).isEqualTo(CostSource.BYOK);
        assertThat(decision.connection().id()).isEqualTo(9L);
        assertThat(decision.apiKey()).isEqualTo("sk-secret");
    }

    @Test
    void brokenByokRulesFailLoudlyInsteadOfFallingBackToPlatform() {
        when(routingRepository.findBySubjectAndTask(7L, ModelTask.LANGUAGE_AGENT))
                .thenReturn(Optional.of(TaskRoutingRule.create(7L, ModelTask.LANGUAGE_AGENT, 9L)
                        .withId(1L)));

        when(connectionRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> router.decide(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("路由规则");

        ModelConnection foreign = ModelConnection.restore(9L, 8L, ModelProvider.DEEPSEEK, "主力",
                URI.create("https://api.deepseek.com/v1"), "deepseek-chat", 5L, true, 1L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> router.decide(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("路由规则");

        ModelConnection disabled = ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK, "主力",
                URI.create("https://api.deepseek.com/v1"), "deepseek-chat", 5L, false, 1L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(disabled));
        assertThatThrownBy(() -> router.decide(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已停用");

        ModelConnection withoutCredential = ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "主力", URI.create("https://api.deepseek.com/v1"), "deepseek-chat", null, true, 1L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(withoutCredential));
        assertThatThrownBy(() -> router.decide(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未绑定凭据");

        when(connectionRepository.findById(9L)).thenReturn(Optional.of(connection()));
        when(vaultRepository.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> router.decide(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("凭据不存在");
    }

    @Test
    void rejectsInvalidSubjectIds() {
        assertThatThrownBy(() -> router.decide(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> router.decide(-1L)).isInstanceOf(IllegalArgumentException.class);
    }
}
