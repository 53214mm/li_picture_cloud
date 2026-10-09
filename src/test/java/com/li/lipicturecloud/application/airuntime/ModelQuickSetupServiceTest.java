package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.application.airuntime.view.ModelDiscoveryView;
import com.li.lipicturecloud.application.airuntime.view.QuickConnectionView;
import com.li.lipicturecloud.domain.airuntime.CredentialVault;
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
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelQuickSetupServiceTest {

    private static final long USER = 7L;
    private static final URI DEEPSEEK = URI.create("https://api.deepseek.com/v1");

    private ModelCatalogClient catalogClient;
    private CredentialService credentialService;
    private ModelConnectionService connectionService;
    private ModelConnectionRepository connectionRepository;
    private ModelRoutingService routingService;
    private TaskRoutingRuleRepository routingRepository;
    private ModelCapabilityProfileService profileService;
    private ModelUsageService usageService;
    private PlatformTransactionManager transactionManager;
    private ModelQuickSetupService service;

    @BeforeEach
    void setUp() {
        catalogClient = mock(ModelCatalogClient.class);
        credentialService = mock(CredentialService.class);
        connectionService = mock(ModelConnectionService.class);
        connectionRepository = mock(ModelConnectionRepository.class);
        routingService = mock(ModelRoutingService.class);
        routingRepository = mock(TaskRoutingRuleRepository.class);
        profileService = mock(ModelCapabilityProfileService.class);
        usageService = mock(ModelUsageService.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = serviceAllowing("api.deepseek.com", "api.siliconflow.cn");
    }

    private ModelQuickSetupService serviceAllowing(String... hosts) {
        return new ModelQuickSetupService(catalogClient, new PropertyEndpointAllowlist(List.of(hosts)),
                credentialService, connectionService, connectionRepository, routingService,
                routingRepository, profileService, usageService, transactionManager);
    }

    @Test
    void discoverReturnsCatalogAndPrefersPresetRecommendation() {
        when(catalogClient.listModels(DEEPSEEK, "sk-key", ModelProvider.DEEPSEEK))
                .thenReturn(ModelCatalogClient.Catalog.of(List.of("deepseek-chat", "deepseek-reasoner", "zzz")));

        ModelDiscoveryView view = service.discover(USER, ModelProvider.DEEPSEEK, "  sk-key  ");

        assertThat(view.listed()).isTrue();
        assertThat(view.models()).containsExactly("deepseek-chat", "deepseek-reasoner", "zzz");
        assertThat(view.recommendedModel()).isEqualTo("deepseek-chat");
        assertThat(view.providerLabel()).isEqualTo("DeepSeek");
        verify(credentialService, never()).store(anyLong(), any(), anyString());
    }

    @Test
    void discoverFallsBackToSuggestionsWhenCatalogIsUnlisted() {
        when(catalogClient.listModels(any(), anyString(), any())).thenReturn(ModelCatalogClient.Catalog.unlisted());

        ModelDiscoveryView view = service.discover(USER, ModelProvider.SILICONFLOW, "sk");

        assertThat(view.listed()).isFalse();
        assertThat(view.models()).containsExactly("deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-7B-Instruct");
        assertThat(view.recommendedModel()).isEqualTo("deepseek-ai/DeepSeek-V3");
    }

    @Test
    void discoverPicksFirstModelWhenRecommendationIsMissing() {
        when(catalogClient.listModels(any(), anyString(), any()))
                .thenReturn(ModelCatalogClient.Catalog.of(List.of("custom-a", "custom-b")));

        assertThat(service.discover(USER, ModelProvider.DEEPSEEK, "sk").recommendedModel()).isEqualTo("custom-a");
    }

    @Test
    void discoverExplainsRejectedKeyAndBlockedProviders() {
        when(catalogClient.listModels(any(), anyString(), any()))
                .thenReturn(ModelCatalogClient.Catalog.failed(ConnectivityResult.CREDENTIAL_REJECTED));

        assertThatThrownBy(() -> service.discover(USER, ModelProvider.DEEPSEEK, "sk"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("API Key 无效");
        assertThatThrownBy(() -> service.discover(USER, ModelProvider.OPENAI, "sk"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("暂未开放");
        assertThatThrownBy(() -> service.discover(USER, ModelProvider.DEEPSEEK, " "))
                .isInstanceOf(BusinessException.class).hasMessageContaining("API Key");
    }

    @Test
    void connectVerifiesBeforeStoringAnything() {
        when(catalogClient.probeChat(DEEPSEEK, "sk", ModelProvider.DEEPSEEK, "deepseek-embed"))
                .thenReturn(ConnectivityResult.failed(ConnectivityResult.MODEL_UNAVAILABLE));

        assertThatThrownBy(() -> service.connect(USER, ModelProvider.DEEPSEEK, "sk", "deepseek-embed"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能用于对话");
        assertThatThrownBy(() -> service.connect(USER, ModelProvider.DEEPSEEK, "sk", "bad model"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("请选择一个模型");
        verify(credentialService, never()).store(anyLong(), any(), anyString());
        verify(connectionService, never()).create(anyLong(), any(), anyString(), any(), anyString(), any());
    }

    @Test
    void connectStoresEnablesSnapshotsAndRoutesLanguageTask() {
        when(catalogClient.probeChat(DEEPSEEK, "sk", ModelProvider.DEEPSEEK, "deepseek-chat"))
                .thenReturn(ConnectivityResult.success());
        when(credentialService.store(USER, ModelProvider.DEEPSEEK, "sk")).thenReturn(
                CredentialVault.restore(5L, USER, ModelProvider.DEEPSEEK, "1234", "cipher",
                        CredentialVault.ALGORITHM_AES_GCM_V1, 0L));
        ModelConnection created = ModelConnection.restore(9L, USER, ModelProvider.DEEPSEEK, "DeepSeek",
                DEEPSEEK, "deepseek-chat", 5L, false, 0L);
        ModelConnection enabled = created.enable();
        when(connectionService.create(USER, ModelProvider.DEEPSEEK, "DeepSeek", DEEPSEEK, "deepseek-chat", 5L))
                .thenReturn(created);
        when(connectionService.enable(9L, USER)).thenReturn(enabled);
        when(routingRepository.findBySubjectAndTask(USER, ModelTask.LANGUAGE_AGENT))
                .thenReturn(Optional.of(TaskRoutingRule.restore(3L, USER, ModelTask.LANGUAGE_AGENT, 9L, 1L)));
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(enabled));

        QuickConnectionView view = service.connect(USER, ModelProvider.DEEPSEEK, "sk", "deepseek-chat");

        assertThat(view.status()).isEqualTo(QuickConnectionView.Status.CONNECTED);
        assertThat(view.providerLabel()).isEqualTo("DeepSeek");
        assertThat(view.modelCode()).isEqualTo("deepseek-chat");
        InOrder order = inOrder(catalogClient, credentialService, connectionService, profileService, routingService);
        order.verify(catalogClient).probeChat(DEEPSEEK, "sk", ModelProvider.DEEPSEEK, "deepseek-chat");
        order.verify(credentialService).store(USER, ModelProvider.DEEPSEEK, "sk");
        order.verify(connectionService).enable(9L, USER);
        order.verify(profileService).snapshotWithVerifiedText(enabled, USER);
        order.verify(routingService).upsert(USER, ModelTask.LANGUAGE_AGENT, 9L);
        verify(usageService).recordSuccess(eq(USER), eq(ModelTask.CONNECTIVITY_CHECK), eq(9L),
                eq(ModelProvider.DEEPSEEK), eq("deepseek-chat"), any());
    }

    @Test
    void currentReportsPlatformConnectedAndBrokenStates() {
        when(routingRepository.findBySubjectAndTask(USER, ModelTask.LANGUAGE_AGENT)).thenReturn(Optional.empty());
        assertThat(service.current(USER).status()).isEqualTo(QuickConnectionView.Status.PLATFORM);

        when(routingRepository.findBySubjectAndTask(USER, ModelTask.LANGUAGE_AGENT))
                .thenReturn(Optional.of(TaskRoutingRule.restore(3L, USER, ModelTask.LANGUAGE_AGENT, null, 1L)));
        assertThat(service.current(USER).status()).isEqualTo(QuickConnectionView.Status.PLATFORM);

        when(routingRepository.findBySubjectAndTask(USER, ModelTask.LANGUAGE_AGENT))
                .thenReturn(Optional.of(TaskRoutingRule.restore(3L, USER, ModelTask.LANGUAGE_AGENT, 9L, 1L)));
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(ModelConnection.restore(9L, USER,
                ModelProvider.DEEPSEEK, "DeepSeek", DEEPSEEK, "deepseek-chat", 5L, false, 1L)));
        assertThat(service.current(USER).status()).isEqualTo(QuickConnectionView.Status.BROKEN);

        when(connectionRepository.findById(9L)).thenReturn(Optional.empty());
        assertThat(service.current(USER).status()).isEqualTo(QuickConnectionView.Status.BROKEN);
    }

    @Test
    void usePlatformClearsOnlyTheLanguageRoute() {
        assertThat(service.usePlatform(USER).status()).isEqualTo(QuickConnectionView.Status.PLATFORM);
        verify(routingService).delete(USER, ModelTask.LANGUAGE_AGENT);
    }
}
