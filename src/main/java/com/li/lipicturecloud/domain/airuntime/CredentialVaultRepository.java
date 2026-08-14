package com.li.lipicturecloud.domain.airuntime;

import java.util.List;
import java.util.Optional;

/**
 * 加密凭据保险库的持久化端口。只读写密文与尾号；明文凭据永不落库。
 */
public interface CredentialVaultRepository {

    Optional<CredentialVault> findById(long id);

    List<CredentialVault> findByOwnerId(long subjectId);

    CredentialVault insert(CredentialVault credential);

    /**
     * 以 revision 为乐观锁覆盖密文；after.revision 必须恰好等于 expectedRevision + 1。
     */
    boolean save(CredentialVault after, long expectedRevision);

    /** 仅在 revision 匹配时删除。 */
    boolean delete(long id, long expectedRevision);
}
