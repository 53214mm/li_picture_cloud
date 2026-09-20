package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.application.airuntime.view.CredentialVaultView;
import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.CredentialVaultRepository;
import com.li.lipicturecloud.domain.airuntime.EncryptedCredential;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 凭据保险库应用服务：存储即加密，轮换走 revision 乐观锁，明文只在 reveal 的返回值中出现一次。
 */
@Service
public class CredentialService {

    private static final int MAX_PLAINTEXT_LENGTH = 512;

    private final CredentialVaultRepository vaultRepository;
    private final CredentialCipher cipher;

    public CredentialService(CredentialVaultRepository vaultRepository, CredentialCipher cipher) {
        this.vaultRepository = vaultRepository;
        this.cipher = cipher;
    }

    public CredentialVault store(long subjectId, ModelProvider provider, String plaintext) {
        checkIdentity(subjectId);
        Objects.requireNonNull(provider, "provider");
        checkPlaintext(plaintext);
        EncryptedCredential encrypted = cipher.encrypt(plaintext);
        return vaultRepository.insert(CredentialVault.create(subjectId, provider,
                encrypted.tail4(), encrypted.cipherText()));
    }

    public CredentialVault rotate(long id, long subjectId, String newPlaintext) {
        checkIdentity(subjectId);
        checkPlaintext(newPlaintext);
        CredentialVault existing = requireOwned(id, subjectId);
        EncryptedCredential encrypted = cipher.encrypt(newPlaintext);
        CredentialVault after = existing.rotateTo(encrypted.tail4(), encrypted.cipherText());
        if (!vaultRepository.save(after, existing.revision())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "凭据轮换发生并发冲突，请重试");
        }
        return after;
    }

    public String reveal(long id, long subjectId) {
        checkIdentity(subjectId);
        return cipher.decrypt(requireOwned(id, subjectId));
    }

    public boolean delete(long id, long subjectId) {
        checkIdentity(subjectId);
        CredentialVault existing = requireOwned(id, subjectId);
        if (!vaultRepository.delete(id, existing.revision())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "凭据删除发生并发冲突，请重试");
        }
        return true;
    }

    /** 保险库索引视图：绝不包含密文，更不包含明文。 */
    public List<CredentialVaultView> list(long subjectId) {
        checkIdentity(subjectId);
        return vaultRepository.findByOwnerId(subjectId).stream()
                .map(CredentialVaultView::of)
                .toList();
    }

    private CredentialVault requireOwned(long id, long subjectId) {
        CredentialVault credential = vaultRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "凭据不存在"));
        if (credential.subjectId() != subjectId) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权操作该凭据");
        }
        return credential;
    }

    private static void checkIdentity(long subjectId) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
    }

    private static void checkPlaintext(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("plaintext must not be blank");
        }
        if (plaintext.length() > MAX_PLAINTEXT_LENGTH) {
            throw new IllegalArgumentException("plaintext must be at most "
                    + MAX_PLAINTEXT_LENGTH + " characters");
        }
    }
}
