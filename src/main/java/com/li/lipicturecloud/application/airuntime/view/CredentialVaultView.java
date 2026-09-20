package com.li.lipicturecloud.application.airuntime.view;

import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;

import java.util.Objects;

/**
 * 凭据保险库的安全索引视图：只有 id、供应商、尾号、算法与版本，永不含密文或明文。
 */
public record CredentialVaultView(
        long id,
        long subjectId,
        ModelProvider provider,
        String tail4,
        String algorithm,
        long revision) {

    public CredentialVaultView {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(tail4, "tail4");
        Objects.requireNonNull(algorithm, "algorithm");
    }

    public static CredentialVaultView of(CredentialVault credential) {
        return new CredentialVaultView(credential.id(), credential.subjectId(),
                credential.provider(), credential.tail4(), credential.algorithm(),
                credential.revision());
    }
}
