package com.li.lipicturecloud.domain.airuntime;

import java.util.List;
import java.util.Optional;

/**
 * 模型连接的持久化端口。凭据引用只保存 credentialId，密文由 CredentialVaultRepository 单独保管。
 */
public interface ModelConnectionRepository {

    Optional<ModelConnection> findById(long id);

    List<ModelConnection> findByOwnerId(long subjectId);

    ModelConnection insert(ModelConnection connection);

    /**
     * 以 revision 为乐观锁写入；after.revision 必须恰好等于 expectedRevision + 1。
     */
    boolean save(ModelConnection after, long expectedRevision);

    /** 仅在 revision 匹配时删除，防止并发修改后误删。 */
    boolean delete(long id, long expectedRevision);
}
