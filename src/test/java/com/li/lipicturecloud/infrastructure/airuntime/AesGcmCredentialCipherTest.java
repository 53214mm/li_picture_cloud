package com.li.lipicturecloud.infrastructure.airuntime;

import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.EncryptedCredential;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmCredentialCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(
            "dev-only-model-credential-key-01".getBytes(StandardCharsets.UTF_8));
    private static final String OTHER_KEY = "62".repeat(32);

    private final AesGcmCredentialCipher cipher = new AesGcmCredentialCipher(KEY);

    @Test
    void encryptThenDecryptRoundTrips() {
        EncryptedCredential encrypted = cipher.encrypt("sk-live-9f8e7d6c5b4a3210");
        assertThat(encrypted.algorithm()).isEqualTo(CredentialVault.ALGORITHM_AES_GCM_V1);
        assertThat(encrypted.cipherText()).contains(":");

        CredentialVault stored = CredentialVault.create(7L, ModelProvider.DEEPSEEK,
                encrypted.tail4(), encrypted.cipherText()).withId(9L);
        assertThat(cipher.decrypt(stored)).isEqualTo("sk-live-9f8e7d6c5b4a3210");
    }

    @Test
    void encryptUsesFreshRandomIvEveryTime() {
        String plaintext = "sk-same-plaintext-twice";
        assertThat(cipher.encrypt(plaintext).cipherText())
                .isNotEqualTo(cipher.encrypt(plaintext).cipherText());
    }

    @Test
    void tailKeepsLastFourAlphanumericCharactersOnly() {
        assertThat(cipher.encrypt("sk-live-9f8e7d6c5b4a").tail4()).isEqualTo("5b4a");
        assertThat(cipher.encrypt("ab").tail4()).isEqualTo("00ab");
        assertThat(cipher.encrypt("----").tail4()).isEqualTo("0000");
    }

    @Test
    void tamperedCiphertextFailsAuthentication() {
        EncryptedCredential encrypted = cipher.encrypt("sk-sensitive");
        String tampered = flipMiddleChar(encrypted.cipherText());
        CredentialVault stored = CredentialVault.create(7L, ModelProvider.DEEPSEEK,
                encrypted.tail4(), tampered).withId(9L);

        assertThatThrownBy(() -> cipher.decrypt(stored)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authentication");
    }

    @Test
    void wrongMasterKeyCannotDecrypt() {
        EncryptedCredential encrypted = cipher.encrypt("sk-sensitive");
        CredentialVault stored = CredentialVault.create(7L, ModelProvider.DEEPSEEK,
                encrypted.tail4(), encrypted.cipherText()).withId(9L);
        AesGcmCredentialCipher other = new AesGcmCredentialCipher(OTHER_KEY);

        assertThatThrownBy(() -> other.decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMalformedCipherText() {
        CredentialVault malformed = CredentialVault.create(7L, ModelProvider.DEEPSEEK, "aB12",
                "no-colon-here").withId(9L);
        assertThatThrownBy(() -> cipher.decrypt(malformed)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeKeyAcceptsHexOrBase64AndRejectsEverythingElse() {
        String hex = "64".repeat(32);
        assertThat(AesGcmCredentialCipher.decodeKey(hex)).hasSize(32);
        assertThat(AesGcmCredentialCipher.decodeKey(KEY)).hasSize(32);

        assertThatThrownBy(() -> AesGcmCredentialCipher.decodeKey(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AesGcmCredentialCipher.decodeKey("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AesGcmCredentialCipher.decodeKey("short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AesGcmCredentialCipher.decodeKey("!!!not-base64!!!"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AesGcmCredentialCipher.decodeKey(
                Base64.getEncoder().encodeToString("short-key-material-12345678".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String flipMiddleChar(String text) {
        int middle = text.length() / 2;
        char flipped = text.charAt(middle) == 'A' ? 'B' : 'A';
        return text.substring(0, middle) + flipped + text.substring(middle + 1);
    }
}
