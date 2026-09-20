package com.li.lipicturecloud.domain.airuntime;

import java.util.Objects;

/**
 * 加密器输出的凭据密文包：尾号用于展示，密文落库，明文只存在于调用方的内存中。
 */
public record EncryptedCredential(String tail4, String cipherText, String algorithm) {

    public EncryptedCredential {
        if (tail4 == null || !tail4.matches("[A-Za-z0-9]{4,8}")) {
            throw new IllegalArgumentException("tail4 must be 4-8 alphanumeric characters");
        }
        if (cipherText == null || cipherText.isBlank() || cipherText.length() > 1024) {
            throw new IllegalArgumentException("cipherText must be 1-1024 characters");
        }
        if (!CredentialVault.ALGORITHM_AES_GCM_V1.equals(algorithm)) {
            throw new IllegalArgumentException("unsupported credential algorithm: " + algorithm);
        }
        Objects.requireNonNull(algorithm, "algorithm");
    }
}
