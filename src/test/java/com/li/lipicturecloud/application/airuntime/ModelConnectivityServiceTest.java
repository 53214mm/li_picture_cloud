package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.CredentialVaultRepository;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelConnectivityServiceTest {

    private static final URI ENDPOINT = URI.create("https://api.deepseek.com/v1");

    private ModelConnectionRepository connectionRepository;
    private CredentialVaultRepository vaultRepository;
    private CredentialCipher cipher;
    private ModelConnectivityTester tester;
    private ModelUsageService usageService;
    private ModelConnectivityService service;

    @BeforeEach
    void setUp() {
        connectionRepository = mock(ModelConnectionRepository.class);
        vaultRepository = mock(CredentialVaultRepository.class);
        cipher = mock(CredentialCipher.class);
        tester = mock(ModelConnectivityTester.class);
        usageService = mock(ModelUsageService.class);
        service = new ModelConnectivityService(connectionRepository, vaultRepository, cipher,
                tester, usageService);
    }

    private ModelConnection connection() {
        return ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK, "主力",
                ENDPOINT, "deepseek-chat", 5L, true, 1L);
    }

    private CredentialVault credential() {
        return CredentialVault.restore(5L, 7L, ModelProvider.DEEPSEEK, "1234",
                "cipher", CredentialVault.ALGORITHM_AES_GCM_V1, 0L);
    }

    @Test
    void reachableConnectionRecordsSuccessUsage() {
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(connection()));
        when(vaultRepository.findById(5L)).thenReturn(Optional.of(credential()));
        when(cipher.decrypt(any(CredentialVault.class))).thenReturn("sk-secret");
        when(tester.test(eq(ENDPOINT), eq("sk-secret"), eq(ModelProvider.DEEPSEEK)))
                .thenReturn(ConnectivityResult.success());

        ConnectivityResult result = service.testConnection(9L, 7L);

        assertThat(result.reachable()).isTrue();
        verify(usageService).recordSuccess(7L, ModelTask.CONNECTIVITY_CHECK, 9L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK);
        verify(usageService, never()).recordFailure(anyLong(), any(), any(), any(), anyString(),
                any(), anyString());
    }

    @Test
    void failedConnectionRecordsFailureWithSameSafeCode() {
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(connection()));
        when(vaultRepository.findById(5L)).thenReturn(Optional.of(credential()));
        when(cipher.decrypt(any(CredentialVault.class))).thenReturn("sk-secret");
        when(tester.test(any(), anyString(), any()))
                .thenReturn(ConnectivityResult.failed(ConnectivityResult.UPSTREAM_TIMEOUT));

        ConnectivityResult result = service.testConnection(9L, 7L);

        assertThat(result.safeErrorCode()).isEqualTo(ConnectivityResult.UPSTREAM_TIMEOUT);
        verify(usageService).recordFailure(7L, ModelTask.CONNECTIVITY_CHECK, 9L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK,
                ConnectivityResult.UPSTREAM_TIMEOUT);
    }

    @Test
    void usageRecordFailureNeverMasksProbeResult() {
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(connection()));
        when(vaultRepository.findById(5L)).thenReturn(Optional.of(credential()));
        when(cipher.decrypt(any(CredentialVault.class))).thenReturn("sk-secret");
        when(tester.test(any(), anyString(), any())).thenReturn(ConnectivityResult.success());
        when(usageService.recordSuccess(anyLong(), any(), any(), any(), anyString(), any()))
                .thenThrow(new IllegalStateException("usage store down"));

        assertThat(service.testConnection(9L, 7L).reachable()).isTrue();
    }

    @Test
    void guardsMissingForeignDisabledAndCredentiallessConnections() {
        when(connectionRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.testConnection(9L, 7L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("模型连接不存在");

        ModelConnection foreign = ModelConnection.restore(9L, 8L, ModelProvider.DEEPSEEK,
                "主力", ENDPOINT, "deepseek-chat", 5L, true, 1L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.testConnection(9L, 7L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("无权操作");

        ModelConnection credentialless = ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "主力", ENDPOINT, "deepseek-chat", null, true, 1L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(credentialless));
        assertThatThrownBy(() -> service.testConnection(9L, 7L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("未绑定凭据");

        ModelConnection disabled = ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "主力", ENDPOINT, "deepseek-chat", 5L, false, 1L);
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(disabled));
        assertThatThrownBy(() -> service.testConnection(9L, 7L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已停用");

        ModelConnection valid = connection();
        when(connectionRepository.findById(9L)).thenReturn(Optional.of(valid));
        when(vaultRepository.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.testConnection(9L, 7L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("凭据不存在");

        CredentialVault foreignCredential = CredentialVault.restore(5L, 8L,
                ModelProvider.DEEPSEEK, "1234", "cipher", CredentialVault.ALGORITHM_AES_GCM_V1, 0L);
        when(vaultRepository.findById(5L)).thenReturn(Optional.of(foreignCredential));
        assertThatThrownBy(() -> service.testConnection(9L, 7L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("凭据不存在");

        verify(tester, never()).test(any(), anyString(), any());
        verify(usageService, Mockito.never()).recordSuccess(anyLong(), any(), any(), any(),
                anyString(), any());
    }
}
