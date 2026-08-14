package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CredentialVaultRepository;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * 模型连接应用服务：创建时校验端点白名单与凭据归属；启停、轮换与删除全部走 revision 乐观锁。
 */
@Service
public class ModelConnectionService {

    private final ModelConnectionRepository connectionRepository;
    private final CredentialVaultRepository vaultRepository;
    private final CredentialService credentialService;
    private final EndpointAllowlist allowlist;

    public ModelConnectionService(ModelConnectionRepository connectionRepository,
                                  CredentialVaultRepository vaultRepository,
                                  CredentialService credentialService,
                                  EndpointAllowlist allowlist) {
        this.connectionRepository = connectionRepository;
        this.vaultRepository = vaultRepository;
        this.credentialService = credentialService;
        this.allowlist = allowlist;
    }

    public ModelConnection create(long subjectId, ModelProvider provider, String displayName,
                                  URI endpoint, String modelCode, Long credentialId) {
        checkIdentity(subjectId);
        Objects.requireNonNull(provider, "provider");
        requireAllowedEndpoint(endpoint);
        if (credentialId != null) {
            requireOwnedCredential(credentialId, subjectId, provider);
        }
        return connectionRepository.insert(ModelConnection.create(subjectId, provider,
                displayName, endpoint, modelCode, credentialId));
    }

    public ModelConnection enable(long id, long subjectId) {
        ModelConnection connection = requireOwnedConnection(id, subjectId);
        requireAllowedEndpoint(connection.endpointUri());
        if (connection.credentialId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "连接必须先绑定凭据才能启用");
        }
        requireOwnedCredential(connection.credentialId(), subjectId, connection.provider());
        return saveOrConflict(connection.enable(), connection.revision());
    }

    public ModelConnection disable(long id, long subjectId) {
        ModelConnection connection = requireOwnedConnection(id, subjectId);
        return saveOrConflict(connection.disable(), connection.revision());
    }

    /** 用新明文创建一条凭据并轮换到该连接上；CAS 冲突时基于最新版本重试一次，避免新凭据成为孤儿。 */
    public ModelConnection rotateCredential(long id, long subjectId, String newPlaintext) {
        ModelConnection connection = requireOwnedConnection(id, subjectId);
        Long nextCredentialId = credentialService.store(subjectId, connection.provider(),
                newPlaintext).id();
        ModelConnection after = connection.rotateCredential(nextCredentialId);
        if (connectionRepository.save(after, connection.revision())) {
            return after;
        }
        ModelConnection latest = requireOwnedConnection(id, subjectId);
        after = latest.rotateCredential(nextCredentialId);
        if (!connectionRepository.save(after, latest.revision())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "连接状态发生并发冲突，请重试");
        }
        return after;
    }

    public boolean delete(long id, long subjectId) {
        ModelConnection connection = requireOwnedConnection(id, subjectId);
        if (!connectionRepository.delete(id, connection.revision())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "连接删除发生并发冲突，请重试");
        }
        return true;
    }

    public List<ModelConnection> list(long subjectId) {
        checkIdentity(subjectId);
        return connectionRepository.findByOwnerId(subjectId);
    }

    /** 归属校验后的连接读取；供控制中心等需要连接本体的内部服务复用。 */
    public ModelConnection findOwned(long id, long subjectId) {
        checkIdentity(subjectId);
        return requireOwnedConnection(id, subjectId);
    }

    private ModelConnection requireOwnedConnection(long id, long subjectId) {
        ModelConnection connection = connectionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模型连接不存在"));
        if (connection.subjectId() != subjectId) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权操作该模型连接");
        }
        return connection;
    }

    private void requireOwnedCredential(long credentialId, long subjectId, ModelProvider provider) {
        vaultRepository.findById(credentialId)
                .filter(credential -> credential.subjectId() == subjectId)
                .filter(credential -> credential.provider() == provider)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAMS_ERROR,
                        "凭据不存在、不属于当前用户或与连接供应商不匹配"));
    }

    private void requireAllowedEndpoint(URI endpoint) {
        if (!allowlist.isAllowed(endpoint)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "端点不在允许清单内");
        }
    }

    private ModelConnection saveOrConflict(ModelConnection after, long expectedRevision) {
        if (!connectionRepository.save(after, expectedRevision)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "连接状态发生并发冲突，请重试");
        }
        return after;
    }

    private static void checkIdentity(long subjectId) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
    }
}
