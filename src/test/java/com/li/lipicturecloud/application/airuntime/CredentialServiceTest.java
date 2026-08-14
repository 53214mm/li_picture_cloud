package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.CredentialVaultRepository;
import com.li.lipicturecloud.domain.airuntime.EncryptedCredential;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

class CredentialServiceTest {

    private CredentialVaultRepository vaultRepository;
    private CredentialCipher cipher;
    private CredentialService service;

    @BeforeEach
    void setUp() {
        vaultRepository = mock(CredentialVaultRepository.class);
        cipher = mock(CredentialCipher.class);
        service = new CredentialService(vaultRepository, cipher);
    }

    @Test
    void storeEncryptsBeforeInserting() {
        when(cipher.encrypt("sk-secret-1234")).thenReturn(new EncryptedCredential(
                "1234", "cipher", CredentialVault.ALGORITHM_AES_GCM_V1));
        when(vaultRepository.insert(any(CredentialVault.class))).thenAnswer(invocation ->
                invocation.<CredentialVault>getArgument(0).withId(9L));

        CredentialVault stored = service.store(7L, ModelProvider.DEEPSEEK, "sk-secret-1234");

        assertThat(stored.id()).isEqualTo(9L);
        verify(vaultRepository).insert(argThatCredential(7L, ModelProvider.DEEPSEEK, "1234"));
    }

    @Test
    void storeRejectsBlankOrOversizedPlaintextBeforeEncrypting() {
        assertThatThrownBy(() -> service.store(7L, ModelProvider.DEEPSEEK, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.store(7L, ModelProvider.DEEPSEEK, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.store(7L, ModelProvider.DEEPSEEK, "x".repeat(513)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.store(0L, ModelProvider.DEEPSEEK, "sk-ok"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(cipher, never()).encrypt(any());
        verify(vaultRepository, never()).insert(any());
    }

    @Test
    void rotateAdvancesRevisionViaCas() {
        CredentialVault existing = CredentialVault.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "1234", "cipher-v1", CredentialVault.ALGORITHM_AES_GCM_V1, 0L);
        when(vaultRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(cipher.encrypt("sk-new-5678")).thenReturn(new EncryptedCredential(
                "5678", "cipher-v2", CredentialVault.ALGORITHM_AES_GCM_V1));
        when(vaultRepository.save(any(CredentialVault.class), eq(0L))).thenReturn(true);

        CredentialVault rotated = service.rotate(9L, 7L, "sk-new-5678");

        assertThat(rotated.revision()).isEqualTo(1L);
        assertThat(rotated.tail4()).isEqualTo("5678");
        verify(vaultRepository).save(rotated, 0L);
    }

    @Test
    void rotateFailsOnConcurrentConflict() {
        CredentialVault existing = CredentialVault.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "1234", "cipher-v1", CredentialVault.ALGORITHM_AES_GCM_V1, 0L);
        when(vaultRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(cipher.encrypt("sk-new-5678")).thenReturn(new EncryptedCredential(
                "5678", "cipher-v2", CredentialVault.ALGORITHM_AES_GCM_V1));
        when(vaultRepository.save(any(CredentialVault.class), eq(0L))).thenReturn(false);

        assertThatThrownBy(() -> service.rotate(9L, 7L, "sk-new-5678"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("并发冲突");
    }

    @Test
    void rotateRejectsForeignCredentials() {
        CredentialVault foreign = CredentialVault.restore(9L, 8L, ModelProvider.DEEPSEEK,
                "1234", "cipher-v1", CredentialVault.ALGORITHM_AES_GCM_V1, 0L);
        when(vaultRepository.findById(9L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.rotate(9L, 7L, "sk-new-5678"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权操作");
    }

    @Test
    void revealDecryptsOwnedCredentialOnly() {
        CredentialVault owned = CredentialVault.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "1234", "cipher-v1", CredentialVault.ALGORITHM_AES_GCM_V1, 0L);
        when(vaultRepository.findById(9L)).thenReturn(Optional.of(owned));
        when(cipher.decrypt(owned)).thenReturn("sk-secret-1234");

        assertThat(service.reveal(9L, 7L)).isEqualTo("sk-secret-1234");

        assertThatThrownBy(() -> service.reveal(99L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("凭据不存在");
        assertThatThrownBy(() -> service.reveal(9L, 8L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权操作");
    }

    @Test
    void deleteUsesRevisionCasAndReportsConflict() {
        CredentialVault owned = CredentialVault.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "1234", "cipher-v1", CredentialVault.ALGORITHM_AES_GCM_V1, 3L);
        when(vaultRepository.findById(9L)).thenReturn(Optional.of(owned));
        when(vaultRepository.delete(9L, 3L)).thenReturn(true);

        assertThat(service.delete(9L, 7L)).isTrue();

        when(vaultRepository.delete(9L, 3L)).thenReturn(false);
        assertThatThrownBy(() -> service.delete(9L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("并发冲突");
    }

    @Test
    void listReturnsSafeViewsWithoutCipherText() {
        CredentialVault stored = CredentialVault.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "1234", "cipher-v1", CredentialVault.ALGORITHM_AES_GCM_V1, 1L);
        when(vaultRepository.findByOwnerId(7L)).thenReturn(List.of(stored));

        List<CredentialVaultView> views = service.list(7L);

        assertThat(views).hasSize(1);
        CredentialVaultView view = views.get(0);
        assertThat(view.id()).isEqualTo(9L);
        assertThat(view.tail4()).isEqualTo("1234");
        assertThat(view.provider()).isEqualTo(ModelProvider.DEEPSEEK);
        assertThat(view.revision()).isEqualTo(1L);
    }

    private static CredentialVault argThatCredential(long subjectId, ModelProvider provider,
                                                     String tail4) {
        return org.mockito.ArgumentMatchers.argThat(credential ->
                credential != null
                        && credential.id() == null
                        && credential.subjectId() == subjectId
                        && credential.provider() == provider
                        && credential.tail4().equals(tail4)
                        && !credential.cipherText().isBlank());
    }
}
