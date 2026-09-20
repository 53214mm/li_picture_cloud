package com.li.lipicturecloud.domain.airuntime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedCredentialTest {

    @Test
    void acceptsWellFormedPackages() {
        EncryptedCredential credential = new EncryptedCredential("aB12", "base64:iv:sealed",
                CredentialVault.ALGORITHM_AES_GCM_V1);
        assertThat(credential.tail4()).isEqualTo("aB12");
        assertThat(credential.cipherText()).isEqualTo("base64:iv:sealed");
    }

    @Test
    void rejectsBadTailsCipherTextsAndAlgorithms() {
        assertThatThrownBy(() -> new EncryptedCredential("ab", "cipher",
                CredentialVault.ALGORITHM_AES_GCM_V1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncryptedCredential("ab!2", "cipher",
                CredentialVault.ALGORITHM_AES_GCM_V1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncryptedCredential("aB12", "  ",
                CredentialVault.ALGORITHM_AES_GCM_V1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncryptedCredential("aB12", "x".repeat(1025),
                CredentialVault.ALGORITHM_AES_GCM_V1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncryptedCredential("aB12", "cipher", "ROT13"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
