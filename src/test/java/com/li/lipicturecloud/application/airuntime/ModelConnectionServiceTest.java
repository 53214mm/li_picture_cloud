package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.CredentialVaultRepository;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.infrastructure.airuntime.PropertyEndpointAllowlist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelConnectionServiceTest {

    private static final URI ALLOWED = URI.create("https://api.deepseek.example/v1");
    private static final URI BLOCKED = URI.create("https://evil.example.com/v1");

    private ModelConnectionRepository connectionRepository;
    private CredentialVaultRepository vaultRepository;
    private CredentialService credentialService;
    private ModelConnectionService service;

    @BeforeEach
    void setUp() {
        connectionRepository = mock(ModelConnectionRepository.class);
        vaultRepository = mock(CredentialVaultRepository.class);
        credentialService = mock(CredentialService.class);
        service = new ModelConnectionService(connectionRepository, vaultRepository,
                credentialService, new PropertyEndpointAllowlist(List.of("deepseek.example")));
    }

    @Test
    void createInsertsWhenEndpointAllowedAndCredentialOwned() {
        CredentialVault owned = CredentialVault.restore(5L, 7L, ModelProvider.DEEPSEEK,
                "1234", "cipher", CredentialVault.ALGORITHM_AES_GCM_V1, 0L);
        when(vaultRepository.findById(5L)).thenReturn(Optional.of(owned));
        when(connectionRepository.insert(any(ModelConnection.class))).thenAnswer(invocation ->
                invocation.<ModelConnection>getArgument(0).withId(9L));

        ModelConnection created = service.create(7L, ModelProvider.DEEPSEEK, "主力",
                ALLOWED, "deepseek-chat", 5L);

        assertThat(created.id()).isEqualTo(9L);
        assertThat(created.credentialId()).isEqualTo(5L);
    }

    @Test
    void createRejectsDisallowedEndpointsAndForeignCredentials() {
        assertThatThrownBy(() -> service.create(7L, ModelProvider.DEEPSEEK, "主力",
                BLOCKED, "deepseek-chat", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("端点不在允许清单内");

        when(vaultRepository.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(7L, ModelProvider.DEEPSEEK, "主力",
                ALLOWED, "deepseek-chat", 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("凭据不存在");

        CredentialVault foreign = CredentialVault.restore(5L, 8L, ModelProvider.DEEPSEEK,
                "1234", "cipher", CredentialVault.ALGORITHM_AES_GCM_V1, 0L);
        when(vaultRepository.findById(5L)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.create(7L, ModelProvider.DEEPSEEK, "主力",
                ALLOWED, "deepseek-chat", 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("凭据不存在");

        // 供应商不匹配的凭据不允许绑定。
        CredentialVault otherProvider = CredentialVault.restore(5L, 7L, ModelProvider.OPENAI,
                "1234", "cipher", CredentialVault.ALGORITHM_AES_GCM_V1, 0L);
        when(vaultRepository.findById(5L)).thenReturn(Optional.of(otherProvider));
        assertThatThrownBy(() -> service.create(7L, ModelProvider.DEEPSEEK, "主力",
                ALLOWED, "deepseek-chat", 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不匹配");

        verify(connectionRepository, never()).insert(any());
    }

    @Test
    void enableRequiresBoundCredentialAndAllowedEndpoint() {
        ModelConnection withoutCredential = ModelConnection.restore(9L, 7L,
                ModelProvider.DEEPSEEK, "主力", ALLOWED, "deepseek-chat", null, false, 0L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(withoutCredential));
        assertThatThrownBy(() -> service.enable(9L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须先绑定凭据");

        ModelConnection withForeignCredential = ModelConnection.restore(9L, 7L,
                ModelProvider.DEEPSEEK, "主力", ALLOWED, "deepseek-chat", 5L, false, 0L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(withForeignCredential));
        when(vaultRepository.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.enable(9L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("凭据不存在");

        ModelConnection blockedEndpoint = ModelConnection.restore(9L, 7L,
                ModelProvider.DEEPSEEK, "主力", BLOCKED, "deepseek-chat", 5L, false, 0L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(blockedEndpoint));
        when(vaultRepository.findById(5L)).thenReturn(Optional.of(ownedCredential(5L, 7L)));
        assertThatThrownBy(() -> service.enable(9L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("端点不在允许清单内");

        verify(connectionRepository, never()).save(any(), anyLong());
    }

    @Test
    void enableAndDisableAdvanceThroughCas() {
        ModelConnection disabled = ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "主力", ALLOWED, "deepseek-chat", 5L, false, 0L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(disabled));
        when(vaultRepository.findById(5L)).thenReturn(Optional.of(ownedCredential(5L, 7L)));
        when(connectionRepository.save(any(ModelConnection.class), eq(0L))).thenReturn(true);

        ModelConnection enabled = service.enable(9L, 7L);
        assertThat(enabled.enabled()).isTrue();
        assertThat(enabled.revision()).isEqualTo(1L);

        when(connectionRepository.findById(9L)).thenReturn(Optional.of(enabled));
        when(connectionRepository.save(any(ModelConnection.class), eq(1L))).thenReturn(true);
        ModelConnection reverted = service.disable(9L, 7L);
        assertThat(reverted.enabled()).isFalse();
        assertThat(reverted.revision()).isEqualTo(2L);
    }

    @Test
    void casConflictsSurfaceAsOperationErrors() {
        ModelConnection disabled = ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "主力", ALLOWED, "deepseek-chat", 5L, false, 0L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(disabled));
        when(vaultRepository.findById(5L)).thenReturn(Optional.of(ownedCredential(5L, 7L)));
        when(connectionRepository.save(any(ModelConnection.class), eq(0L))).thenReturn(false);

        assertThatThrownBy(() -> service.enable(9L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("并发冲突");
    }

    @Test
    void rotateCredentialStoresNewSecretAndBindsIt() {
        ModelConnection existing = ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "主力", ALLOWED, "deepseek-chat", 5L, true, 2L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(credentialService.store(7L, ModelProvider.DEEPSEEK, "sk-new-5678"))
                .thenReturn(CredentialVault.restore(22L, 7L, ModelProvider.DEEPSEEK,
                        "5678", "cipher-v2", CredentialVault.ALGORITHM_AES_GCM_V1, 0L));
        when(connectionRepository.save(any(ModelConnection.class), eq(2L))).thenReturn(true);

        ModelConnection rotated = service.rotateCredential(9L, 7L, "sk-new-5678");

        assertThat(rotated.credentialId()).isEqualTo(22L);
        assertThat(rotated.revision()).isEqualTo(3L);
    }

    @Test
    void rotateCredentialRetriesOnceOnCasConflictSoTheNewCredentialIsNeverOrphaned() {
        ModelConnection existing = ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "主力", ALLOWED, "deepseek-chat", 5L, true, 2L);
        when(credentialService.store(7L, ModelProvider.DEEPSEEK, "sk-new-5678"))
                .thenReturn(CredentialVault.restore(22L, 7L, ModelProvider.DEEPSEEK,
                        "5678", "cipher-v2", CredentialVault.ALGORITHM_AES_GCM_V1, 0L));
        // 第一次 CAS 冲突；重读拿到最新版本 5，第二次 CAS 成功。
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(existing),
                Optional.of(ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                        "主力", ALLOWED, "deepseek-chat", 5L, true, 5L)));
        when(connectionRepository.save(any(ModelConnection.class), eq(2L))).thenReturn(false);
        when(connectionRepository.save(any(ModelConnection.class), eq(5L))).thenReturn(true);

        ModelConnection rotated = service.rotateCredential(9L, 7L, "sk-new-5678");

        assertThat(rotated.credentialId()).isEqualTo(22L);
        assertThat(rotated.revision()).isEqualTo(6L);
    }

    @Test
    void deleteRequiresOwnershipAndCas() {
        ModelConnection owned = ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "主力", ALLOWED, "deepseek-chat", 5L, false, 1L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(owned));
        when(connectionRepository.delete(9L, 1L)).thenReturn(true);

        assertThat(service.delete(9L, 7L)).isTrue();

        when(connectionRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(9L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模型连接不存在");

        ModelConnection foreign = ModelConnection.restore(9L, 8L, ModelProvider.DEEPSEEK,
                "主力", ALLOWED, "deepseek-chat", 5L, false, 1L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.delete(9L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权操作");
    }

    @Test
    void listReturnsAllOwnedConnections() {
        ModelConnection connection = ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "主力", ALLOWED, "deepseek-chat", 5L, false, 0L);
        when(connectionRepository.findByOwnerId(7L)).thenReturn(List.of(connection));

        assertThat(service.list(7L)).containsExactly(connection);
        assertThatThrownBy(() -> service.list(0L)).isInstanceOf(IllegalArgumentException.class);
    }

    private static CredentialVault ownedCredential(long id, long subjectId) {
        return CredentialVault.restore(id, subjectId, ModelProvider.DEEPSEEK, "1234",
                "cipher", CredentialVault.ALGORITHM_AES_GCM_V1, 0L);
    }
}
