package com.li.lipicturecloud.domain.airuntime;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 加密凭据条目：只存密文与尾号，明文永不出现在领域层、日志或接口响应中。
 */
public record CredentialVault(
        Long id,
        long subjectId,
        ModelProvider provider,
        String tail4,
        String cipherText,
        String algorithm,
        long revision) {

    public static final String ALGORITHM_AES_GCM_V1 = "AES_GCM_V1";
    private static final Pattern TAIL = Pattern.compile("[A-Za-z0-9]{4,8}");

    public CredentialVault {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (subjectId <= 0 || revision < 0) {
            throw new IllegalArgumentException("invalid credential identity or revision");
        }
        Objects.requireNonNull(provider, "provider");
        if (tail4 == null || !TAIL.matcher(tail4).matches()) {
            throw new IllegalArgumentException("tail4 must be 4-8 alphanumeric characters");
        }
        if (cipherText == null || cipherText.isBlank() || cipherText.length() > 1024) {
            throw new IllegalArgumentException("cipherText must be 1-1024 characters");
        }
        if (!ALGORITHM_AES_GCM_V1.equals(algorithm)) {
            throw new IllegalArgumentException("unsupported credential algorithm: " + algorithm);
        }
    }

    public static CredentialVault create(long subjectId, ModelProvider provider,
                                         String tail4, String cipherText) {
        return new CredentialVault(null, subjectId, provider, tail4, cipherText,
                ALGORITHM_AES_GCM_V1, 0L);
    }

    public static CredentialVault restore(Long id, long subjectId, ModelProvider provider,
                                          String tail4, String cipherText, String algorithm,
                                          long revision) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("persisted id must be positive");
        }
        return new CredentialVault(id, subjectId, provider, tail4, cipherText, algorithm, revision);
    }

    public CredentialVault withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new CredentialVault(persistedId, subjectId, provider, tail4, cipherText,
                algorithm, revision);
    }
}
