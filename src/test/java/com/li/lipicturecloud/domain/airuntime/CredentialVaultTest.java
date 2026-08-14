package com.li.lipicturecloud.domain.airuntime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialVaultTest {

    private static final String CIPHER = "base64:QUVTLUdDTS1jaXBoZXJ0ZXh0LXBsYWNlaG9sZGVy";

    @Test
    void createYieldsAesGcmV1CipherEntryAtRevisionZero() {
        CredentialVault credential = CredentialVault.create(7L, ModelProvider.DEEPSEEK,
                "aB12", CIPHER);

        assertThat(credential.id()).isNull();
        assertThat(credential.subjectId()).isEqualTo(7L);
        assertThat(credential.provider()).isEqualTo(ModelProvider.DEEPSEEK);
        assertThat(credential.tail4()).isEqualTo("aB12");
        assertThat(credential.cipherText()).isEqualTo(CIPHER);
        assertThat(credential.algorithm()).isEqualTo(CredentialVault.ALGORITHM_AES_GCM_V1);
        assertThat(credential.revision()).isZero();
    }

    @Test
    void rejectsInvalidIdentitiesAndRevisions() {
        assertThatThrownBy(() -> CredentialVault.create(0L, ModelProvider.DEEPSEEK, "aB12",
                CIPHER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CredentialVault.restore(null, 7L, ModelProvider.DEEPSEEK, "aB12",
                CIPHER, CredentialVault.ALGORITHM_AES_GCM_V1, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CredentialVault.restore(0L, 7L, ModelProvider.DEEPSEEK, "aB12",
                CIPHER, CredentialVault.ALGORITHM_AES_GCM_V1, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CredentialVault.restore(-3L, 7L, ModelProvider.DEEPSEEK, "aB12",
                CIPHER, CredentialVault.ALGORITHM_AES_GCM_V1, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CredentialVault.restore(1L, 7L, ModelProvider.DEEPSEEK, "aB12",
                CIPHER, CredentialVault.ALGORITHM_AES_GCM_V1, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullProviderAndBadTails() {
        assertThatThrownBy(() -> CredentialVault.create(7L, null, "aB12",
                CIPHER)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CredentialVault.create(7L, ModelProvider.DEEPSEEK, null,
                CIPHER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CredentialVault.create(7L, ModelProvider.DEEPSEEK, "ab",
                CIPHER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CredentialVault.create(7L, ModelProvider.DEEPSEEK, "ab!2",
                CIPHER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CredentialVault.create(7L, ModelProvider.DEEPSEEK, "abcdefghi",
                CIPHER)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBadCipherTextsAndAlgorithms() {
        assertThatThrownBy(() -> CredentialVault.create(7L, ModelProvider.DEEPSEEK, "aB12",
                null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CredentialVault.create(7L, ModelProvider.DEEPSEEK, "aB12",
                "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CredentialVault.create(7L, ModelProvider.DEEPSEEK, "aB12",
                "x".repeat(1025))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CredentialVault.restore(1L, 7L, ModelProvider.DEEPSEEK, "aB12",
                CIPHER, "ROT13", 0L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withIdAssignsPersistedIdExactlyOnce() {
        CredentialVault created = CredentialVault.create(7L, ModelProvider.DEEPSEEK, "aB12",
                CIPHER);

        CredentialVault persisted = created.withId(11L);
        assertThat(persisted.id()).isEqualTo(11L);

        assertThatThrownBy(() -> persisted.withId(12L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> created.withId(0L)).isInstanceOf(IllegalStateException.class);
    }
}
